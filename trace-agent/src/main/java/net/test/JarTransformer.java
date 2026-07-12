package net.test;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.pool.TypePool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import net.test.DelegateFieldPatcher.DelegateField;

public class JarTransformer {

  public static void transform(List<TraceAction> actions, DefaultArguments defaultArgs, String inputJarPath, String outputJarPath) throws Exception {

    JarFile jarFile = new JarFile(inputJarPath);
    ClassFileLocator locator = ClassFileLocator.ForJarFile.of(new File(inputJarPath));
    try {
      // Compose the locator with the system and boot class loaders so the TypePool can resolve
      // java.lang.Object and other JDK types referenced by classes in the target JAR.
      ClassFileLocator compositeLocator =
          new ClassFileLocator.Compound(locator, ClassFileLocator.ForClassLoader.ofBootLoader(), ClassFileLocator.ForClassLoader.of(ClassLoader.getSystemClassLoader()));
      TypePool typePool = TypePool.Default.of(compositeLocator);
      ByteBuddy byteBuddy = new ByteBuddy();

      List<String> classNames = collectClassNames(jarFile);

      Manifest manifest = jarFile.getManifest();
      if (manifest == null) {
        manifest = new Manifest();
      }
      annotateManifest(manifest, actions);
      JarOutputStream out = new JarOutputStream(new FileOutputStream(outputJarPath), manifest);

      Set<String> writtenEntries = new HashSet<String>();
      // META-INF/MANIFEST.MF is already written by the JarOutputStream(manifest) constructor,
      // but the bare META-INF/ directory entry still needs to be copied from the input JAR.
      writtenEntries.add("META-INF/MANIFEST.MF");

      try {
        for (String className : classNames) {
          TypeDescription typeDesc = typePool.describe(className).resolve();

          List<TraceAction> matching = matchingActions(actions, typeDesc);

          String classEntry = className.replace('.', '/') + ".class";
          if (matching.isEmpty()) {
            JarEntry entry = jarFile.getJarEntry(classEntry);
            putEntry(out, writtenEntries, entry.getName());
            copyStream(jarFile.getInputStream(entry), out);
          } else {
            System.out.println("JarTransformer transforming: " + className);
            DynamicType.Builder<?> builder = byteBuddy.rebase(typeDesc, locator);
            // Track interceptors in application order so we can match them to delegate$ fields
            List<Object> interceptors = new ArrayList<Object>();
            List<TraceAction> appliedActions = new ArrayList<TraceAction>();
            for (TraceAction action : matching) {
              Object interceptor = action.getActionInterceptor(defaultArgs);
              if (interceptor != null) {
                builder = builder.method(action.getMethodMatcher()).intercept(MethodDelegation.to(interceptor));
                interceptors.add(interceptor);
                appliedActions.add(action);
              }
            }
            DynamicType.Unloaded<?> made = builder.make();
            byte[] classBytes = made.getBytes();

            // Patch the generated <clinit> to initialize the delegate$ fields that ByteBuddy
            // leaves null in offline mode (they are only set by LoadedTypeInitializer in agent mode).
            classBytes = patchDelegateFields(classBytes, interceptors, appliedActions);

            putEntry(out, writtenEntries, classEntry);
            out.write(classBytes);
            for (Map.Entry<TypeDescription, byte[]> aux : made.getAuxiliaryTypes().entrySet()) {
              String auxPath = aux.getKey().getName().replace('.', '/') + ".class";
              putEntry(out, writtenEntries, auxPath);
              out.write(aux.getValue());
            }
          }
        }

        // Copy non-class entries (resources, etc.)
        for (JarEntry entry : Collections.list(jarFile.entries())) {
          String name = entry.getName();
          if (!name.endsWith(".class") && !writtenEntries.contains(name)) {
            putEntry(out, writtenEntries, name);
            if (!entry.isDirectory()) {
              copyStream(jarFile.getInputStream(entry), out);
            }
          }
        }

        // Inject agent support classes so the instrumented JAR is self-contained
        injectAgentClasses(out, writtenEntries);

      } finally {
        out.close();
      }
    } finally {
      locator.close();
      jarFile.close();
    }
  }

  private static void annotateManifest(Manifest manifest, List<TraceAction> actions) {
    java.util.jar.Attributes main = manifest.getMainAttributes();
    for (int i = 0; i < actions.size(); i++) {
      main.putValue("Trace-Agent-Action-" + (i + 1), actions.get(i).toActionLine());
    }
  }

  private static List<String> collectClassNames(JarFile jarFile) {
    List<String> names = new ArrayList<String>();
    for (JarEntry entry : Collections.list(jarFile.entries())) {
      String name = entry.getName();
      if (name.endsWith(".class") && !name.equals("module-info.class")) {
        names.add(name.replace('/', '.').substring(0, name.length() - ".class".length()));
      }
    }
    return names;
  }

  private static List<TraceAction> matchingActions(List<TraceAction> actions, TypeDescription typeDesc) {
    List<TraceAction> result = new ArrayList<TraceAction>();
    for (TraceAction action : actions) {
      if (action.getClassMatcher().matches(typeDesc)) {
        result.add(action);
      }
    }
    return result;
  }

  /**
   * Matches the {@code delegate$} fields in the generated class bytes to the applied interceptors by type name, builds {@link DelegateField} descriptors, and patches the class's
   * {@code <clinit>} to initialize those fields at runtime.
   */
  private static byte[] patchDelegateFields(byte[] classBytes, List<Object> interceptors, List<TraceAction> appliedActions) {
    List<String[]> delegateFields = DelegateFieldPatcher.collectDelegateFields(classBytes);
    if (delegateFields.isEmpty()) {
      return classBytes;
    }
    // Build a map from interceptorInternalName -> actionArgs for quick lookup.
    // (If the same interceptor type appears more than once we match in list order.)
    List<DelegateField> toInit = new ArrayList<DelegateField>();
    for (String[] fieldInfo : delegateFields) {
      String fieldName = fieldInfo[0];
      String interceptorInternalName = fieldInfo[1];
      // Find the first unmatched action whose interceptor class matches
      String actionArgs = null;
      for (int i = 0; i < interceptors.size(); i++) {
        String candidateName = interceptors.get(i).getClass().getName().replace('.', '/');
        if (candidateName.equals(interceptorInternalName)) {
          actionArgs = appliedActions.get(i).getActionArgs();
          interceptors.remove(i);
          appliedActions.remove(i);
          break;
        }
      }
      toInit.add(new DelegateField(fieldName, interceptorInternalName, actionArgs));
    }
    return DelegateFieldPatcher.patch(classBytes, toInit);
  }

  // Inject all net.test (and net.test.interceptor) classes from the running agent JAR
  // into the output JAR so it is self-contained at runtime.
  private static void injectAgentClasses(JarOutputStream out, Set<String> writtenEntries) throws IOException {
    String agentJarPath = getAgentJarPath();
    if (agentJarPath == null) {
      System.err.println("JarTransformer: could not locate agent JAR for class injection; " + "interceptor classes will not be bundled in the output JAR");
      return;
    }
    JarFile agentJar = new JarFile(agentJarPath);
    try {
      for (JarEntry entry : Collections.list(agentJar.entries())) {
        String name = entry.getName();
        if (isAgentClass(name) && !writtenEntries.contains(name)) {
          putEntry(out, writtenEntries, name);
          if (!entry.isDirectory()) {
            copyStream(agentJar.getInputStream(entry), out);
          }
        }
      }
    } finally {
      agentJar.close();
    }
  }

  private static void putEntry(JarOutputStream out, Set<String> writtenEntries, String name) throws IOException {
    out.putNextEntry(new JarEntry(name));
    writtenEntries.add(name);
  }

  private static boolean isAgentClass(String entryName) {
    // Include all net/test/ classes (agent support + interceptors) and the ByteBuddy library
    // classes needed at runtime by the instrumented code.
    return entryName.startsWith("net/test/") || entryName.startsWith("net/bytebuddy/");
  }

  private static String getAgentJarPath() {
    // Locate the JAR that contains this class — that is the running agent fat JAR.
    java.net.URL url = JarTransformer.class.getProtectionDomain().getCodeSource().getLocation();
    if (url == null) {
      return null;
    }
    String path = url.getPath();
    if (path.endsWith(".jar")) {
      return path;
    }
    return null;
  }

  static void copyStream(InputStream in, java.io.OutputStream out) throws IOException {
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) != -1) {
      out.write(buf, 0, n);
    }
  }
}
