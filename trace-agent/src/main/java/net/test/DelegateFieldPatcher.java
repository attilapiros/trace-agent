package net.test;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.FieldVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

/**
 * Patches a ByteBuddy-generated class's {@code <clinit>} to initialize the synthetic {@code delegate$xxx} static fields that MethodDelegation.to(instance) creates but leaves null
 * in offline (non-agent) mode.
 *
 * <p>For each {@code delegate$xxx} field of an interceptor type, it appends bytecode equivalent to:
 *
 * <pre>
 * delegate$xxx = new SomeInterceptor(
 *     new GlobalArguments(System.out),
 *     actionArgs,
 *     OfflineDefaultArguments.INSTANCE
 * );
 * </pre>
 */
class DelegateFieldPatcher {

  private static final String DELEGATE_PREFIX = "delegate$";

  /** Info extracted from a delegate field found in the generated class. */
  static class DelegateField {
    final String fieldName;
    final String interceptorInternalName; // e.g. "net/test/interceptor/TimingInterceptorMs"
    final String actionArgs; // may be null

    DelegateField(String fieldName, String interceptorInternalName, String actionArgs) {
      this.fieldName = fieldName;
      this.interceptorInternalName = interceptorInternalName;
      this.actionArgs = actionArgs;
    }
  }

  /**
   * Given the generated class bytes and the per-action actionArgs strings (one per matching action, in the order the actions were applied), patches the class to initialize all
   * {@code delegate$} fields in {@code <clinit>}.
   */
  static byte[] patch(final byte[] classBytes, final List<DelegateField> delegateFields) {
    ClassReader cr = new ClassReader(classBytes);
    ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

    cr.accept(
        new ClassVisitor(Opcodes.ASM9, cw) {
          private String ownerInternalName;

          @Override
          public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.ownerInternalName = name;
            super.visit(version, access, name, signature, superName, interfaces);
          }

          @Override
          public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if ("<clinit>".equals(name)) {
              // The class already has a <clinit>; inject before its RETURN
              return new ClinitInjector(mv, ownerInternalName, delegateFields);
            }
            return mv;
          }

          @Override
          public void visitEnd() {
            // If no <clinit> existed yet, create one
            if (ownerInternalName != null) {
              // We cannot know here whether <clinit> was visited; we always inject via the visitor
              // above if it exists. If the class had no <clinit>, ByteBuddy typically generates one
              // for the Method cache field, so this case should not arise in practice.
            }
            super.visitEnd();
          }
        },
        0);

    return cw.toByteArray();
  }

  /** Collects delegate field info ({@code delegate$} fields) from a generated class's bytes, in encounter order. Returns pairs of (fieldName, interceptorInternalName). */
  static List<String[]> collectDelegateFields(byte[] classBytes) {
    ClassReader cr = new ClassReader(classBytes);
    final List<String[]> fields = new ArrayList<String[]>();
    cr.accept(
        new ClassVisitor(Opcodes.ASM9) {
          @Override
          public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (name.startsWith(DELEGATE_PREFIX)) {
              // descriptor is e.g. "Lnet/test/interceptor/TimingInterceptorMs;" — strip L and ;
              String internalName = descriptor.substring(1, descriptor.length() - 1);
              fields.add(new String[] {name, internalName});
            }
            return null;
          }
        },
        ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
    return fields;
  }

  /** MethodVisitor that injects delegate-field initialization before each RETURN in {@code <clinit>}. */
  private static class ClinitInjector extends MethodVisitor {

    private final String ownerInternalName;
    private final List<DelegateField> delegateFields;

    ClinitInjector(MethodVisitor mv, String ownerInternalName, List<DelegateField> delegateFields) {
      super(Opcodes.ASM9, mv);
      this.ownerInternalName = ownerInternalName;
      this.delegateFields = delegateFields;
    }

    @Override
    public void visitInsn(int opcode) {
      if (opcode == Opcodes.RETURN) {
        emitFieldInitializations();
      }
      super.visitInsn(opcode);
    }

    private void emitFieldInitializations() {
      for (DelegateField df : delegateFields) {
        String interceptorDesc = "L" + df.interceptorInternalName + ";";

        // new SomeInterceptor(...)
        mv.visitTypeInsn(Opcodes.NEW, df.interceptorInternalName);
        mv.visitInsn(Opcodes.DUP);

        // arg 1: new GlobalArguments(System.out)
        mv.visitTypeInsn(Opcodes.NEW, "net/test/GlobalArguments");
        mv.visitInsn(Opcodes.DUP);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/test/GlobalArguments", "<init>", "(Ljava/io/PrintStream;)V", false);

        // arg 2: actionArgs string (may be null)
        if (df.actionArgs == null) {
          mv.visitInsn(Opcodes.ACONST_NULL);
        } else {
          mv.visitLdcInsn(df.actionArgs);
        }

        // arg 3: OfflineDefaultArguments.INSTANCE
        mv.visitFieldInsn(Opcodes.GETSTATIC, "net/test/OfflineDefaultArguments", "INSTANCE", "Lnet/test/OfflineDefaultArguments;");

        // invoke interceptor constructor
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, df.interceptorInternalName, "<init>", "(Lnet/test/GlobalArguments;Ljava/lang/String;Lnet/test/DefaultArguments;)V", false);

        // putstatic Owner.delegate$xxx
        mv.visitFieldInsn(Opcodes.PUTSTATIC, ownerInternalName, df.fieldName, interceptorDesc);
      }
    }
  }
}
