package cn.glfs.apply.annotions;

public @interface Lazy {
    boolean value() default true;
}
