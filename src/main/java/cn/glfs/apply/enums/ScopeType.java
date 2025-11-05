package cn.glfs.apply.enums;

public enum ScopeType {
    SINGLETON("singleton"),
    PROTOTYPE("prototype");

    private final String value;

    ScopeType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ScopeType ofValue(String value) {
        for (ScopeType scope : values()) {
            if (scope.value.equalsIgnoreCase(value)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unknown scope: " + value);
    }
}