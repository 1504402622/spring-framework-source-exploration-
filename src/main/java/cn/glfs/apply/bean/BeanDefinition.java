package cn.glfs.apply.bean;

import cn.glfs.apply.enums.ScopeType;

import java.util.Objects;

public class BeanDefinition {
    private Object beanObject;
    private Class beanClass;
    private ScopeType scope;// 单例/多例
    private String isLazy;// 是否懒加载

    public Object getBeanObject() {
        return beanObject;
    }
    public void setBeanObject(Object beanObject) {
        this.beanObject = beanObject;
    }

    public Class getBeanClass() {
        return beanClass;
    }

    public void setBeanClass(Class beanClass) {
        this.beanClass = beanClass;
    }

    public ScopeType getScope() {
        return scope;
    }

    public void setScope(ScopeType scope) {
        this.scope = scope;
    }

    public String getIsLazy() {
        return isLazy;
    }

    public void setIsLazy(String isLazy) {
        this.isLazy = isLazy;
    }

}
