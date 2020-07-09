#include <jni.h>

JNIEXPORT jstring JNICALL
Java_com_getkeepsafe_dexcount_integration_MainActivity_stringFromJNI(JNIEnv* env, jclass clazz)
{
  return env->NewStringUTF("Hello from JNI!");
}
