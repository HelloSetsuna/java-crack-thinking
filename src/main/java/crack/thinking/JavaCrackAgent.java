package crack.thinking;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/**
 * Java Crack Agent
 * 该类尝试修改掉 Java 原生类 java.security.Signature 的 verify 方法的返回值，使其永远返回 true 的一个实现样例
 * 基于该思路，理论上可以修改任何 Java 原生类的方法返回值，包括但不仅限于一些加密类，这在绕过一些 Java 开发的软件
 * 的安全验证机制上有时会有一些不错的效果，请以研究和学习的目的合法使用该源码。
 *
 * 声明：该类仅用于研究和学习 javaagent 及 javassist 工具的使用，源码将于我的 github 上开源， 任何人都可以下载和
 * 尝试使用，但本人不对其可能造成的任何影响承担任何法律责任。
 *
 * @author derick.setsuna.jin 2021-01-21 14:40:00
 * @version 1.0
 **/
public class JavaCrackAgent {


    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new ClassFileTransformer() {

            // 类加载期间不需考虑并发
            private int rewriteId = 0;

            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                // 避免不必要的判断 减少 javaagent 对类加载时间的影响
                if (rewriteId > 1) {
                    return null;
                }
                // 使用 javassist 工具修改 java 自身的 signature 的返回值， 使其一直返回 true
                if ("java/security/Signature".equals(className)) {
                    try {
                        final ClassPool classPool = ClassPool.getDefault();
                        final CtClass clazz = classPool.get("java.security.Signature");
                        CtMethod[] methods = clazz.getDeclaredMethods("verify");
                        for (CtMethod method : methods) {
                            System.out.println("\n [" + rewriteId + "] JavaCrackAgent rewrite java.security.Signature # verify(...)\n");
                            method.setBody("{return true;}");
                        }
                        byte[] byteCode = clazz.toBytecode();
                        // detach 的意思是将内存中曾经被 javassist 加载过的类移除
                        // 如果下次有需要在内存中 找不到会 重新走 javassist 加载
                        clazz.detach();
                        rewriteId ++;
                        return byteCode;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }

                // 因上面修改了 java.security.Signature 的字节码， 所以可能会导致 jce 的 JarVerifier 对 jar 包的签名验证过不去
                // 最简单粗暴的方案就是将 javax.crypto.JarVerifier 的 建议逻辑函数 testSignatures 的字节码一样给修改掉， ok ~
                else if ("javax/crypto/JarVerifier".equals(className)) {
                    try {
                        final ClassPool classPool = ClassPool.getDefault();
                        final CtClass clazz = classPool.get("javax.crypto.JarVerifier");
                        CtMethod method = clazz.getDeclaredMethod("testSignatures");
                        System.out.println("\n [" + rewriteId + "] JavaCrackAgent rewrite javax.crypto.JarVerifier # testSignatures(...)\n");
                        method.setBody("{}");
                        byte[] byteCode = clazz.toBytecode();
                        clazz.detach();
                        rewriteId ++;
                        return byteCode;
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                return null;
            }
        });
    }
}
