package cafe;

/** Vai trò quyết định ứng dụng mà tài khoản được phép sử dụng. */
public enum Role {
    STAFF("Nhân viên"),
    ADMIN("Quản trị viên");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean canAccess(Role requiredRole) {
        return requiredRole != null && (this == ADMIN || this == requiredRole);
    }
}
