package com.fluxpass.model;

public class PasswordEntry {

    private final String fullPath;
    private final String name;
    private final String category;
    private String password;
    private String metadata;

    public PasswordEntry(String fullPath) {
        this.fullPath = fullPath;
        int lastSlash = fullPath.lastIndexOf('/');
        if (lastSlash >= 0) {
            this.category = fullPath.substring(0, lastSlash);
            this.name = fullPath.substring(lastSlash + 1);
        } else {
            this.category = "";
            this.name = fullPath;
        }
    }

    public String getFullPath() {
        return fullPath;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public boolean hasMetadata() {
        return metadata != null && !metadata.isBlank();
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PasswordEntry that)) return false;
        return fullPath.equals(that.fullPath);
    }

    @Override
    public int hashCode() {
        return fullPath.hashCode();
    }
}
