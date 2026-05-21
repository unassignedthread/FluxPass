package com.fluxpass.ui;

import com.fluxpass.FluxPassApp;
import com.fluxpass.model.PasswordEntry;
import com.fluxpass.service.PassService;
import com.fluxpass.service.PassService.PassException;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.net.URI;
import java.util.List;

public class EntryDialog extends Dialog<EntryDialog.Result> {

    public enum Mode { ADD, EDIT }

    public enum EntryType {
        WEBSITE("Website"),
        NOTE("Note"),
        GENERIC("Generic");

        private final String displayName;
        EntryType(String displayName) { this.displayName = displayName; }
        @Override
        public String toString() { return displayName; }
    }

    public record Result(String path, String fullContent) {}

    private final PassService passService;
    private final Mode mode;
    private final List<String> folders;

    private ComboBox<EntryType> typeCombo;
    private EntryType detectedType;
    private ComboBox<String> folderCombo;
    private TextField nameField;
    private Label nameLabel;

    private TextField urlField;
    private TextField loginField;
    private TextField passwordField;
    private PasswordField hiddenPasswordField;
    private Button togglePasswordBtn;
    private Button copyBtn;
    private Spinner<Integer> lengthSpinner;
    private CheckBox noSymbolsCheck;

    private TextArea notesArea;
    private TextArea contentArea;
    private TextField titleField;

    private Label statusLabel;
    private Label passwordLabel;
    private HBox passwordBox;
    private GridPane grid;
    private boolean passwordVisible;

    private int typeRow;
    private HBox generateBox;
    private boolean hidePasswordForNote;
    private final String themeCss;

    public EntryDialog(PassService passService, Mode mode, PasswordEntry existingEntry, List<String> folders, String themeCss) {
        this.passService = passService;
        this.mode = mode;
        this.folders = folders;
        this.passwordVisible = false;
        this.hidePasswordForNote = false;
        this.themeCss = themeCss;
        buildDialog(existingEntry);
    }

    private void buildDialog(PasswordEntry existingEntry) {
        setTitle(mode == Mode.EDIT ? "Edit Password Entry" : "Add Password Entry");
        setHeaderText(null);
        setResizable(true);

        setOnShowing(e -> {
            javafx.stage.Stage stage = (javafx.stage.Stage) getDialogPane().getScene().getWindow();
            javafx.scene.image.Image icon = FluxPassApp.getAppIcon();
            if (icon != null && stage.getIcons().isEmpty()) {
                stage.getIcons().add(icon);
            }
        });

        DialogPane pane = getDialogPane();
        pane.getStyleClass().add("dialog-pane");
        pane.getStylesheets().add(getClass().getResource(themeCss).toExternalForm());
        pane.setPrefWidth(460);
        pane.setMinWidth(420);
        pane.setPrefHeight(400);
        pane.setMinHeight(320);

        grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));

        int row = 0;

        addComboRow(grid, row++, "Type:", typeCombo = new ComboBox<>());
        typeCombo.getItems().addAll(EntryType.values());
        typeCombo.setValue(EntryType.WEBSITE);
        typeCombo.setPrefWidth(280);

        if (mode == Mode.ADD) {
            addComboRow(grid, row++, "Folder:", folderCombo = new ComboBox<>());
            folderCombo.setEditable(true);
            folderCombo.setPrefWidth(280);
            folderCombo.setPromptText("Root (or select/create folder)");
            for (String f : folders) {
                folderCombo.getItems().add(f.isEmpty() ? "(root)" : f);
            }

            nameLabel = new Label("Name:");
            nameLabel.getStyleClass().add("dialog-label");
            nameField = new TextField();
            nameField.setPromptText("e.g. personal, work");
            nameField.setPrefWidth(280);
            grid.add(nameLabel, 0, row);
            grid.add(nameField, 1, row);
            row++;
        } else {
            Label pathLabel = new Label("Path:");
            pathLabel.getStyleClass().add("dialog-label");
            Label pathValue = new Label(existingEntry.getFullPath());
            pathValue.setStyle("-fx-font-weight: bold;");
            grid.add(pathLabel, 0, row);
            grid.add(pathValue, 1, row);
            row++;
        }

        buildPasswordWidgets();

        typeRow = row;

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #4CAF50;");
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        GridPane.setColumnSpan(statusLabel, 2);
        grid.add(statusLabel, 0, row);
        row++;

        typeCombo.setOnAction(e -> rebuildTypeFields());

        if (mode == Mode.ADD) {
            rebuildTypeFields();
        } else if (existingEntry != null) {
            loadExistingEntry(existingEntry);
        }

        pane.setContent(grid);

        ButtonType saveType = new ButtonType(mode == Mode.EDIT ? "Save" : "Create", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        Button saveButton = (Button) pane.lookupButton(saveType);
        saveButton.getStyleClass().add("button-primary");
        saveButton.setDefaultButton(true);

        setResultConverter(buttonType -> {
            if (buttonType == saveType) {
                return buildResult(existingEntry);
            }
            return null;
        });

        Platform.runLater(() -> {
            if (mode == Mode.ADD && nameField != null) {
                nameField.requestFocus();
            }
        });
    }

    private void buildPasswordWidgets() {
        passwordLabel = new Label("Password:");
        passwordLabel.getStyleClass().add("dialog-label");

        passwordBox = new HBox(4);
        passwordBox.setAlignment(Pos.CENTER_LEFT);

        hiddenPasswordField = new PasswordField();
        hiddenPasswordField.setPrefWidth(190);
        hiddenPasswordField.getStyleClass().add("password-field");

        passwordField = new TextField();
        passwordField.setPrefWidth(190);
        passwordField.getStyleClass().add("password-field");
        passwordField.setVisible(false);
        passwordField.setManaged(false);

        togglePasswordBtn = new Button("\uD83D\uDC41");
        togglePasswordBtn.getStyleClass().add("button-default");
        togglePasswordBtn.setTooltip(new Tooltip("Show/Hide"));
        togglePasswordBtn.setOnAction(e -> togglePasswordVisibility());

        copyBtn = new Button("\uD83D\uDCCB");
        copyBtn.getStyleClass().add("button-default");
        copyBtn.setTooltip(new Tooltip("Copy"));
        copyBtn.setOnAction(e -> copyToClipboard());

        passwordBox.getChildren().addAll(hiddenPasswordField, passwordField, togglePasswordBtn, copyBtn);

        generateBox = new HBox(6);
        generateBox.setAlignment(Pos.CENTER_LEFT);

        lengthSpinner = new Spinner<>(4, 128, 25);
        lengthSpinner.setPrefWidth(65);
        lengthSpinner.setEditable(true);

        noSymbolsCheck = new CheckBox("No symbols");
        noSymbolsCheck.setSelected(false);

        Button genBtn = new Button("Generate");
        genBtn.getStyleClass().add("button-default");
        genBtn.setOnAction(e -> generatePassword());

        generateBox.getChildren().addAll(new Label("Len:"), lengthSpinner, noSymbolsCheck, genBtn);
    }

    private void addPasswordRow(int row) {
        if (hidePasswordForNote) {
            passwordLabel.setVisible(false);
            passwordLabel.setManaged(false);
            passwordBox.setVisible(false);
            passwordBox.setManaged(false);
            return;
        }
        passwordLabel.setVisible(true);
        passwordLabel.setManaged(true);
        passwordBox.setVisible(true);
        passwordBox.setManaged(true);
        grid.add(passwordLabel, 0, row);
        grid.add(passwordBox, 1, row);
    }

    private void addGenerateRow(int row) {
        if (hidePasswordForNote) {
            generateBox.setVisible(false);
            generateBox.setManaged(false);
            return;
        }
        generateBox.setVisible(true);
        generateBox.setManaged(true);
        grid.add(new Label(""), 0, row);
        grid.add(generateBox, 1, row);
    }

    private void addComboRow(GridPane g, int row, String label, ComboBox<?> cb) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("dialog-label");
        g.add(lbl, 0, row);
        g.add(cb, 1, row);
    }

    private void addLabelField(int row, String label, Control field) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("dialog-label");
        grid.add(lbl, 0, row);
        grid.add(field, 1, row);
    }

    private void rebuildTypeFields() {
        grid.getChildren().removeIf(n -> {
            Integer r = GridPane.getRowIndex(n);
            return r != null && r >= typeRow && r < typeRow + 20;
        });

        EntryType type = typeCombo != null ? typeCombo.getValue() : EntryType.GENERIC;
        if (type == null) type = EntryType.GENERIC;

        int row = typeRow;

        if (type == EntryType.WEBSITE) {
            if (mode == Mode.ADD) {
                if (urlField == null) { urlField = new TextField(); urlField.setPrefWidth(280); }
                urlField.setPromptText("https://example.com");
                addLabelField(row++, "URL:", urlField);
                urlField.textProperty().addListener((obs, oldVal, newVal) -> {
                    nameField.setPromptText(deriveHostname(newVal));
                });
            }

            if (loginField == null) { loginField = new TextField(); loginField.setPrefWidth(280); }
            loginField.setPromptText("user@example.com");
            addLabelField(row++, "Login:", loginField);

            hidePasswordForNote = false;
            passwordLabel.setText("Password:");
            addPasswordRow(row++);
            addGenerateRow(row++);

        } else if (type == EntryType.NOTE) {
            hidePasswordForNote = true;

            if (titleField == null) { titleField = new TextField(); titleField.setPrefWidth(280); }
            titleField.setPromptText("Note title");
            addLabelField(row++, "Title:", titleField);

            if (contentArea == null) { contentArea = new TextArea(); contentArea.setPrefRowCount(6); contentArea.setPrefWidth(280); contentArea.getStyleClass().add("metadata-area"); contentArea.setWrapText(true); }
            contentArea.setPromptText("Note content...");
            addLabelField(row++, "Content:", contentArea);

        } else {
            hidePasswordForNote = false;
            passwordLabel.setText("Password:");
            addPasswordRow(row++);
            addGenerateRow(row++);

            if (notesArea == null) { notesArea = new TextArea(); notesArea.setPrefRowCount(4); notesArea.setPrefWidth(280); notesArea.getStyleClass().add("metadata-area"); notesArea.setWrapText(true); }
            notesArea.setPromptText("Optional notes...");
            addLabelField(row++, "Notes:", notesArea);
        }

        grid.add(statusLabel, 0, row);
    }

    private Result buildResult(PasswordEntry existingEntry) {
        if (mode == Mode.EDIT) {
            return buildEditResult(existingEntry);
        }

        EntryType type = typeCombo.getValue();

        if (type == EntryType.WEBSITE) {
            return buildWebsiteResult();
        } else if (type == EntryType.NOTE) {
            return buildNoteResult();
        } else {
            return buildGenericResult();
        }
    }

    private Result buildEditResult(PasswordEntry existingEntry) {
        String path = existingEntry.getFullPath();
        EntryType type = typeCombo != null ? typeCombo.getValue() : detectedType;
        if (type == null) type = EntryType.GENERIC;

        StringBuilder content = new StringBuilder();

        if (type == EntryType.WEBSITE) {
            String pw = getCurrentPassword();
            if (pw.isEmpty()) { showAlert("Password cannot be empty."); return null; }
            content.append(pw).append('\n');
            if (loginField != null && !loginField.getText().isBlank()) {
                content.append("login: ").append(loginField.getText().trim()).append('\n');
            }
        } else if (type == EntryType.NOTE) {
            content.append("note\n");
            if (titleField != null && !titleField.getText().isBlank()) {
                content.append("title: ").append(titleField.getText().trim()).append('\n');
            }
            if (contentArea != null && !contentArea.getText().isBlank()) {
                content.append(contentArea.getText().trim());
            }
        } else {
            String pw = getCurrentPassword();
            if (pw.isEmpty()) { showAlert("Password cannot be empty."); return null; }
            content.append(pw).append('\n');
            if (notesArea != null && !notesArea.getText().isBlank()) {
                content.append(notesArea.getText().trim());
            }
        }

        return new Result(path, content.toString());
    }

    private Result buildWebsiteResult() {
        String hostname = urlField != null ? deriveHostname(urlField.getText()) : "untitled";
        if (hostname == null || hostname.isBlank()) hostname = "untitled";

        String userName = nameField.getText().trim();

        String folder = getFolder();
        StringBuilder path = new StringBuilder();
        if (!folder.isEmpty()) path.append(folder).append('/');
        path.append(hostname);
        if (!userName.isEmpty()) path.append('/').append(userName);

        String pw = getCurrentPassword();
        if (pw.isEmpty()) {
            showAlert("Password cannot be empty.");
            return null;
        }

        StringBuilder content = new StringBuilder(pw);
        content.append('\n');
        if (loginField != null && !loginField.getText().isBlank()) {
            content.append("login: ").append(loginField.getText().trim()).append('\n');
        }

        return new Result(path.toString(), content.toString());
    }

    private Result buildNoteResult() {
        String rawName = nameField.getText().trim();
        if (rawName.isEmpty()) {
            showAlert("Name cannot be empty.");
            return null;
        }

        String folder = getFolder();
        String path = folder.isEmpty() ? rawName : folder + "/" + rawName;

        String title = titleField != null && !titleField.getText().isBlank()
                ? titleField.getText().trim() : rawName;

        StringBuilder content = new StringBuilder("note\n");
        content.append("title: ").append(title).append('\n');

        if (contentArea != null && !contentArea.getText().isBlank()) {
            content.append(contentArea.getText().trim());
        }

        return new Result(path, content.toString());
    }

    private Result buildGenericResult() {
        String rawName = nameField.getText().trim();
        if (rawName.isEmpty()) {
            showAlert("Name cannot be empty.");
            return null;
        }

        String folder = getFolder();
        String path = folder.isEmpty() ? rawName : folder + "/" + rawName;

        String pw = getCurrentPassword();
        if (pw.isEmpty()) {
            showAlert("Password cannot be empty.");
            return null;
        }

        StringBuilder content = new StringBuilder(pw);
        content.append('\n');
        if (notesArea != null && !notesArea.getText().isBlank()) {
            content.append(notesArea.getText().trim());
        }

        return new Result(path, content.toString());
    }

    private String getFolder() {
        if (folderCombo == null) return "";
        String val = folderCombo.getEditor().getText().trim();
        if (!val.isEmpty() && !val.equals("(root)")) return val;
        if (folderCombo.getValue() != null && !folderCombo.getValue().equals("(root)")) return folderCombo.getValue();
        return "";
    }

    private void loadExistingEntry(PasswordEntry entry) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return passService.show(entry.getFullPath());
            }
        };
        task.setOnSucceeded(e -> {
            String content = task.getValue();
            if (content != null && !content.isEmpty()) {
                String[] parts = content.split("\n", 2);
                String firstLine = parts[0];
                String meta = parts.length > 1 ? parts[1] : "";

                if ("note".equals(firstLine)) {
                    detectedType = EntryType.NOTE;
                    hidePasswordForNote = true;
                    parseNoteMetadata(meta);
                } else if (meta.contains("login:") || meta.contains("url:") || meta.contains("user:")) {
                    detectedType = EntryType.WEBSITE;
                    hiddenPasswordField.setText(firstLine);
                    passwordField.setText(firstLine);
                    parseWebsiteMetadata(meta);
                } else {
                    detectedType = EntryType.GENERIC;
                    hiddenPasswordField.setText(firstLine);
                    passwordField.setText(firstLine);
                    notesArea = new TextArea();
                    notesArea.setText(meta);
                    notesArea.setPrefRowCount(4);
                    notesArea.setPrefWidth(280);
                    notesArea.getStyleClass().add("metadata-area");
                    notesArea.setWrapText(true);
                }

                typeCombo.setValue(detectedType);
            }
            Platform.runLater(() -> rebuildTypeFields());
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            if (ex instanceof PassException pe) {
                showAlert("Error loading entry: " + pe.getMessage());
            }
        });
        new Thread(task).start();
    }

    private void parseWebsiteMetadata(String meta) {
        for (String line : meta.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            int colon = trimmed.indexOf(':');
            if (colon < 0) continue;
            String key = trimmed.substring(0, colon).toLowerCase();
            String value = trimmed.substring(colon + 1).trim();
            if (value.isEmpty()) continue;
            if (key.equals("login") || key.equals("user")) {
                loginField = new TextField();
                loginField.setText(value);
                loginField.setPrefWidth(280);
            }
        }
    }

    private void parseNoteMetadata(String meta) {
        String title = "";
        StringBuilder content = new StringBuilder();
        for (String line : meta.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.toLowerCase().startsWith("title:")) {
                title = trimmed.substring(6).trim();
            } else {
                if (!content.isEmpty()) content.append('\n');
                content.append(trimmed);
            }
        }
        titleField = new TextField();
        titleField.setText(title);
        titleField.setPrefWidth(280);
        contentArea = new TextArea();
        contentArea.setText(content.toString());
        contentArea.setPrefRowCount(6);
        contentArea.setPrefWidth(280);
        contentArea.getStyleClass().add("metadata-area");
        contentArea.setWrapText(true);
    }

    private String getCurrentPassword() {
        return passwordVisible ? passwordField.getText() : hiddenPasswordField.getText();
    }

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        String current = passwordVisible ? hiddenPasswordField.getText() : passwordField.getText();
        if (passwordVisible) {
            passwordField.setText(current);
            hiddenPasswordField.setVisible(false);
            hiddenPasswordField.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
        } else {
            hiddenPasswordField.setText(current);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            hiddenPasswordField.setVisible(true);
            hiddenPasswordField.setManaged(true);
        }
        togglePasswordBtn.setText(passwordVisible ? "\uD83D\uDE48" : "\uD83D\uDC41");
    }

    private void copyToClipboard() {
        String pw = getCurrentPassword();
        if (pw.isEmpty()) return;
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(pw);
        clipboard.setContent(content);
        statusLabel.setText("Copied.");
    }

    private void generatePassword() {
        int length = lengthSpinner.getValue();
        boolean noSymbols = noSymbolsCheck.isSelected();
        String pw = generatePasswordString(length, noSymbols);
        hiddenPasswordField.setText(pw);
        passwordField.setText(pw);
        statusLabel.setText("Generated.");
    }

    private String generatePasswordString(int length, boolean noSymbols) {
        StringBuilder sb = new StringBuilder();
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String symbols = "!@#$%^&*()_+-=[]{}|;:,.<>?";

        String charset = upper + lower + digits + (noSymbols ? "" : symbols);
        java.security.SecureRandom random = new java.security.SecureRandom();

        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        if (!noSymbols) {
            sb.append(symbols.charAt(random.nextInt(symbols.length())));
        }

        int remaining = length - sb.length();
        for (int i = 0; i < remaining; i++) {
            sb.append(charset.charAt(random.nextInt(charset.length())));
        }

        char[] chars = sb.toString().toCharArray();
        for (int i = 0; i < chars.length; i++) {
            int j = random.nextInt(chars.length);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }

        return new String(chars);
    }

    private String deriveHostname(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            String input = url.startsWith("http") ? url : "https://" + url;
            String host = URI.create(input).toURL().getHost();
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception e) {
            return "";
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.getDialogPane().getStylesheets().add(getClass().getResource(themeCss).toExternalForm());
        alert.showAndWait();
    }
}
