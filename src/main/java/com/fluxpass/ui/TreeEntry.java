package com.fluxpass.ui;

import com.fluxpass.model.PasswordEntry;

public class TreeEntry {

    private final String name;
    private final boolean directory;
    private final PasswordEntry passwordEntry;

    private TreeEntry(String name, boolean directory, PasswordEntry passwordEntry) {
        this.name = name;
        this.directory = directory;
        this.passwordEntry = passwordEntry;
    }

    public static TreeEntry directory(String name) {
        return new TreeEntry(name, true, null);
    }

    public static TreeEntry leaf(PasswordEntry entry) {
        return new TreeEntry(entry.getName(), false, entry);
    }

    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return directory;
    }

    public PasswordEntry getPasswordEntry() {
        return passwordEntry;
    }

    @Override
    public String toString() {
        return name;
    }
}
