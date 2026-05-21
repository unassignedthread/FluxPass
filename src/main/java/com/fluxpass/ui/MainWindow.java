package com.fluxpass.ui;

import com.fluxpass.FluxPassApp;
import com.fluxpass.model.PasswordEntry;
import com.fluxpass.service.PassService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MainWindow {

    private final Stage stage;
    private final PassService passService;
    private final ScheduledExecutorService clipboardExecutor;

    private Scene scene;
    private TreeView<TreeEntry> treeView;
    private TextField searchField;
    private Label nameValueLabel;
    private TextField passwordField;
    private PasswordField hiddenPasswordField;
    private Button togglePasswordBtn;
    private Button copyBtn;
    private Label passLabel;
    private HBox passBox;
    private TextArea metadataArea;
    private VBox parsedFieldsBox;
    private Label metaLabel;
    private VBox emptyState;
    private VBox detailsContent;

    private PasswordEntry selectedEntry;
    private boolean passwordVisible;
    private boolean darkMode = true;
    private List<PasswordEntry> allEntries = new ArrayList<>();

    private TrayIcon trayIcon;
    private ScheduledFuture<?> clipboardClearFuture;

    public MainWindow(Stage stage) {
        this.stage = stage;
        this.passService = new PassService();
        this.clipboardExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "clipboard-clearer");
            t.setDaemon(true);
            return t;
        });
    }

    public void show() {
        if (!passService.isPassAvailable()) {
            showError("pass not found",
                    "The 'pass' utility is not installed or not found in PATH.\n\n" +
                    "Install it with your package manager and initialize a password store with:\n" +
                    "  pass init <your-gpg-key-id>");
            return;
        }

        BorderPane root = new BorderPane();

        root.setTop(buildToolbar());

        root.setLeft(buildTreePanel());
        root.setCenter(buildDetailsPanel());

        scene = new Scene(root, 750, 480);
        applyTheme();
        stage.setScene(scene);
        stage.setTitle("FluxPass");

        javafx.scene.image.Image icon = FluxPassApp.getAppIcon();
        if (icon != null) {
            stage.getIcons().add(icon);
        }

        stage.setOnCloseRequest(e -> {
            clipboardExecutor.shutdownNow();
            if (trayIcon != null && SystemTray.isSupported()) {
                try {
                    SystemTray.getSystemTray().remove(trayIcon);
                } catch (Exception ignored) {}
            }
            Platform.exit();
        });

        setupSystemTray();
        stage.show();
        loadEntries();
    }

    private void applyTheme() {
        scene.getStylesheets().clear();
        scene.getStylesheets().add(getClass().getResource(
                darkMode ? "/styles-dark.css" : "/styles.css").toExternalForm());
    }

    private HBox buildToolbar() {
        HBox toolbar = new HBox(6);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(6, 10, 6, 10));

        searchField = new TextField();
        searchField.setPromptText("Search entries...");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterTree(newVal));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button themeBtn = new Button("☀");
        themeBtn.setTooltip(new Tooltip("Toggle dark/light theme"));
        themeBtn.getStyleClass().add("button-default");
        themeBtn.setOnAction(e -> {
            darkMode = !darkMode;
            applyTheme();
            themeBtn.setText(darkMode ? "☀" : "☾");
        });

        Button addBtn = new Button("+ Add");
        addBtn.getStyleClass().add("button-primary");
        addBtn.setOnAction(e -> showAddDialog());

        toolbar.getChildren().addAll(searchField, spacer, themeBtn, addBtn);
        return toolbar;
    }

    private TreeView<TreeEntry> buildTreePanel() {
        TreeItem<TreeEntry> rootItem = new TreeItem<>(TreeEntry.directory("Password Store"));
        rootItem.setExpanded(true);
        treeView = new TreeView<>(rootItem);
        treeView.setShowRoot(false);
        treeView.setPrefWidth(230);
        treeView.getStyleClass().add("tree-view");
        treeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(TreeEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.getName());
                    getStyleClass().add("tree-cell");
                }
            }
        });

        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null && !newVal.getValue().isDirectory()) {
                onEntrySelected(newVal.getValue().getPasswordEntry());
            }
        });

        treeView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                TreeItem<TreeEntry> selected = treeView.getSelectionModel().getSelectedItem();
                if (selected != null && selected.getValue() != null && !selected.getValue().isDirectory()) {
                    onEntrySelected(selected.getValue().getPasswordEntry());
                }
            }
        });

        return treeView;
    }

    private VBox buildDetailsPanel() {
        VBox detailsPanel = new VBox();
        detailsPanel.getStyleClass().add("details-panel");

        emptyState = new VBox(16);
        emptyState.setAlignment(Pos.CENTER);
        emptyState.setPadding(new Insets(40, 40, 40, 40));
        Label emptyIcon = new Label("\uD83D\uDD11");
        emptyIcon.setStyle("-fx-font-size: 48px;");
        Label emptyText = new Label("Select an entry to view details");
        emptyText.getStyleClass().add("empty-state");
        Label emptyHint = new Label("Ctrl+F to focus search");
        emptyHint.setStyle("-fx-font-size: 12px; -fx-text-fill: #bdbdbd;");
        emptyState.getChildren().addAll(emptyIcon, emptyText, emptyHint);

        detailsContent = new VBox(10);
        detailsContent.setVisible(false);
        detailsContent.setManaged(false);

        Label nameLabel = new Label("Entry name");
        nameLabel.getStyleClass().add("details-label");
        nameValueLabel = new Label();
        nameValueLabel.getStyleClass().add("details-value");

        Label passLabel = new Label("Password");
        this.passLabel = passLabel;
        passLabel.getStyleClass().add("details-label");

        HBox passBox = new HBox(4);
        this.passBox = passBox;
        passBox.setAlignment(Pos.CENTER_LEFT);

        hiddenPasswordField = new PasswordField();
        hiddenPasswordField.setEditable(false);
        hiddenPasswordField.getStyleClass().add("password-field");
        hiddenPasswordField.setPrefWidth(260);

        passwordField = new TextField();
        passwordField.setEditable(false);
        passwordField.getStyleClass().add("password-field");
        passwordField.setPrefWidth(260);
        passwordField.setVisible(false);
        passwordField.setManaged(false);

        togglePasswordBtn = new Button("\uD83D\uDC41");
        togglePasswordBtn.setTooltip(new Tooltip("Show/Hide password"));
        togglePasswordBtn.getStyleClass().add("button-default");
        togglePasswordBtn.setOnAction(e -> toggleDetailsPassword());

        copyBtn = new Button("\uD83D\uDCCB Copy");
        copyBtn.getStyleClass().add("button-default");
        copyBtn.setTooltip(new Tooltip("Copy password to clipboard"));
        copyBtn.setOnAction(e -> copyDetailsPassword());

        passBox.getChildren().addAll(hiddenPasswordField, passwordField, togglePasswordBtn, copyBtn);

        parsedFieldsBox = new VBox(4);
        parsedFieldsBox.setVisible(false);
        parsedFieldsBox.setManaged(false);
        parsedFieldsBox.setPadding(new Insets(4, 0, 4, 0));

        metaLabel = new Label("Metadata");
        metaLabel.getStyleClass().add("details-label");
        metadataArea = new TextArea();
        metadataArea.setEditable(false);
        metadataArea.setWrapText(true);
        metadataArea.setPrefRowCount(6);
        metadataArea.getStyleClass().add("metadata-area");

        HBox editDeleteBox = new HBox(8);
        editDeleteBox.setAlignment(Pos.CENTER_LEFT);
        editDeleteBox.setPadding(new Insets(6, 0, 0, 0));

        Button editBtn = new Button("\u270E Edit");
        editBtn.getStyleClass().add("button-default");
        editBtn.setOnAction(e -> showEditDialog());

        Button deleteBtn = new Button("\u2717 Delete");
        deleteBtn.getStyleClass().add("button-danger");
        deleteBtn.setOnAction(e -> deleteEntry());

        editDeleteBox.getChildren().addAll(editBtn, deleteBtn);

        detailsContent.getChildren().addAll(
                nameLabel, nameValueLabel,
                passLabel, passBox,
                parsedFieldsBox,
                metaLabel, metadataArea,
                editDeleteBox
        );

        detailsPanel.getChildren().addAll(emptyState, detailsContent);
        VBox.setVgrow(detailsContent, Priority.ALWAYS);
        VBox.setVgrow(metadataArea, Priority.ALWAYS);

        return detailsPanel;
    }

    private void onEntrySelected(PasswordEntry entry) {
        this.selectedEntry = entry;
        nameValueLabel.setText(entry.getFullPath());
        hiddenPasswordField.clear();
        passwordField.clear();
        metadataArea.clear();
        metadataArea.setVisible(false);
        metadataArea.setManaged(false);
        metaLabel.setVisible(false);
        metaLabel.setManaged(false);
        passwordVisible = false;

        passLabel.setVisible(true);
        passLabel.setManaged(true);
        passBox.setVisible(true);
        passBox.setManaged(true);

        parsedFieldsBox.getChildren().clear();
        parsedFieldsBox.setVisible(false);
        parsedFieldsBox.setManaged(false);

        hiddenPasswordField.setVisible(true);
        hiddenPasswordField.setManaged(true);
        passwordField.setVisible(false);
        passwordField.setManaged(false);
        togglePasswordBtn.setText("\uD83D\uDC41");

        emptyState.setVisible(false);
        emptyState.setManaged(false);
        detailsContent.setVisible(true);
        detailsContent.setManaged(true);

        Task<Void> task = new Task<>() {
            private String fullContent;
            @Override
            protected Void call() throws Exception {
                fullContent = passService.show(entry.getFullPath());
                return null;
            }
            @Override
            protected void succeeded() {
                if (fullContent != null && !fullContent.isEmpty()) {
                    String[] lines = fullContent.split("\n", 2);
                    String firstLine = lines[0];
                    boolean isNote = "note".equals(firstLine);

                    passLabel.setVisible(!isNote);
                    passLabel.setManaged(!isNote);
                    passBox.setVisible(!isNote);
                    passBox.setManaged(!isNote);

                    hiddenPasswordField.setText(firstLine);
                    passwordField.setText(firstLine);
                    if (lines.length > 1) {
                        parseMetadata(lines[1]);
                    }
                }
            }
            @Override
            protected void failed() {
                Throwable ex = getException();
                showError("Failed to load entry", ex.getMessage());
            }
        };
        new Thread(task).start();
    }

    private void parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) return;

        StringBuilder remaining = new StringBuilder();
        boolean hasKnown = false;

        for (String line : metadata.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String lower = trimmed.toLowerCase();

            int colon = trimmed.indexOf(':');
            if (colon > 0) {
                String key = lower.substring(0, colon);
                String value = trimmed.substring(colon + 1).trim();
                if (value.isEmpty()) {
                    if (!remaining.isEmpty()) remaining.append('\n');
                    remaining.append(trimmed);
                    continue;
                }
                if (key.equals("login")) {
                    addDetailRow("Login", value);
                    hasKnown = true;
                } else if (key.equals("url")) {
                    addDetailRow("URL", value);
                    hasKnown = true;
                } else if (key.equals("title")) {
                    addDetailRow("Title", value);
                    hasKnown = true;
                } else if (key.equals("user") || key.equals("username") || key.equals("email")) {
                    String label = trimmed.substring(0, colon);
                    addDetailRow(label, value);
                    hasKnown = true;
                } else {
                    if (!remaining.isEmpty()) remaining.append('\n');
                    remaining.append(trimmed);
                }
            } else {
                if (!remaining.isEmpty()) remaining.append('\n');
                remaining.append(trimmed);
            }
        }

        if (hasKnown) {
            parsedFieldsBox.setVisible(true);
            parsedFieldsBox.setManaged(true);
        }

        if (!remaining.isEmpty()) {
            metadataArea.setText(remaining.toString());
            metadataArea.setVisible(true);
            metadataArea.setManaged(true);
            metaLabel.setVisible(true);
            metaLabel.setManaged(true);
        }
    }

    private void addDetailRow(String label, String value) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("details-label");
        Label val = new Label(value);
        val.getStyleClass().add("details-value");
        val.setWrapText(true);
        HBox row = new HBox(8);
        row.getChildren().addAll(lbl, val);
        parsedFieldsBox.getChildren().add(row);
    }

    private void toggleDetailsPassword() {
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

    private void copyDetailsPassword() {
        String pw = hiddenPasswordField.isVisible()
                ? hiddenPasswordField.getText()
                : passwordField.getText();

        if (pw.isEmpty()) return;

        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(pw);
        clipboard.setContent(content);

        copyBtn.setText("\u2713 Copied");
        copyBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");

        if (clipboardClearFuture != null && !clipboardClearFuture.isDone()) {
            clipboardClearFuture.cancel(false);
        }

        clipboardClearFuture = clipboardExecutor.schedule(() -> {
            Platform.runLater(() -> {
                javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
                String current = cb.getString();
                if (current != null && current.equals(pw)) {
                    javafx.scene.input.ClipboardContent empty = new javafx.scene.input.ClipboardContent();
                    empty.putString("");
                    cb.setContent(empty);
                }
                copyBtn.setText("\uD83D\uDCCB Copy");
                copyBtn.setStyle("");
                copyBtn.getStyleClass().add("button-default");
            });
        }, 30, TimeUnit.SECONDS);
    }

    private List<String> collectFolders() {
        Set<String> folders = new TreeSet<>();
        folders.add("");
        for (PasswordEntry entry : allEntries) {
            String cat = entry.getCategory();
            if (!cat.isEmpty()) {
                folders.add(cat);
                String[] parts = cat.split("/");
                String prefix = "";
                for (int i = 0; i < parts.length - 1; i++) {
                    prefix = prefix.isEmpty() ? parts[i] : prefix + "/" + parts[i];
                    folders.add(prefix);
                }
            }
        }
        return new ArrayList<>(folders);
    }

    private String getThemeCss() {
        return darkMode ? "/styles-dark.css" : "/styles.css";
    }

    private void showAddDialog() {
        List<String> folders = collectFolders();
        EntryDialog dialog = new EntryDialog(passService, EntryDialog.Mode.ADD, null, folders, getThemeCss());
        Optional<EntryDialog.Result> result = dialog.showAndWait();
        result.ifPresent(r -> createEntry(r.path(), r.fullContent()));
    }

    private void showEditDialog() {
        if (selectedEntry == null) return;
        List<String> folders = collectFolders();
        EntryDialog dialog = new EntryDialog(passService, EntryDialog.Mode.EDIT, selectedEntry, folders, getThemeCss());
        Optional<EntryDialog.Result> result = dialog.showAndWait();
        result.ifPresent(r -> updateEntry(r.path(), r.fullContent()));
    }

    private void createEntry(String path, String fullContent) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                passService.insert(path, fullContent);
                return null;
            }
            @Override
            protected void succeeded() {
                loadEntries();
            }
            @Override
            protected void failed() {
                showError("Failed to create entry", getException().getMessage());
            }
        };
        new Thread(task).start();
    }

    private void updateEntry(String path, String fullContent) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                passService.insert(path, fullContent);
                return null;
            }
            @Override
            protected void succeeded() {
                if (selectedEntry != null && selectedEntry.getFullPath().equals(path)) {
                    onEntrySelected(selectedEntry);
                }
                loadEntries();
            }
            @Override
            protected void failed() {
                showError("Failed to update entry", getException().getMessage());
            }
        };
        new Thread(task).start();
    }

    private void deleteEntry() {
        if (selectedEntry == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Entry");
        confirm.setHeaderText("Delete \"" + selectedEntry.getFullPath() + "\"?");
        confirm.setContentText("This action cannot be undone.");
        confirm.getDialogPane().getStylesheets().add(getClass().getResource(getThemeCss()).toExternalForm());

        Optional<ButtonType> response = confirm.showAndWait();
        if (response.isPresent() && response.get() == ButtonType.OK) {
            String path = selectedEntry.getFullPath();
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    passService.delete(path);
                    return null;
                }
                @Override
                protected void succeeded() {
                    selectedEntry = null;
                    emptyState.setVisible(true);
                    emptyState.setManaged(true);
                    detailsContent.setVisible(false);
                    detailsContent.setManaged(false);
                    loadEntries();
                }
                @Override
                protected void failed() {
                    showError("Failed to delete entry", getException().getMessage());
                }
            };
            new Thread(task).start();
        }
    }

    private void loadEntries() {
        Task<List<PasswordEntry>> task = new Task<>() {
            @Override
            protected List<PasswordEntry> call() throws Exception {
                return passService.listEntries();
            }
        };
        task.setOnSucceeded(e -> {
            allEntries = task.getValue();
            buildTree(allEntries);
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            showError("Failed to load entries", ex != null ? ex.getMessage() : "Unknown error");
        });
        new Thread(task).start();
    }

    private void buildTree(List<PasswordEntry> entries) {
        TreeItem<TreeEntry> root = new TreeItem<>(TreeEntry.directory("Password Store"));
        root.setExpanded(true);

        Map<String, TreeItem<TreeEntry>> dirMap = new HashMap<>();
        dirMap.put("", root);

        for (PasswordEntry entry : entries) {
            String category = entry.getCategory();
            if (category.isEmpty()) {
                root.getChildren().add(new TreeItem<>(TreeEntry.leaf(entry)));
            } else {
                String[] parts = category.split("/");
                String currentPath = "";
                TreeItem<TreeEntry> parent = root;
                for (String part : parts) {
                    String newPath = currentPath.isEmpty() ? part : currentPath + "/" + part;
                    TreeItem<TreeEntry> dirItem = dirMap.get(newPath);
                    if (dirItem == null) {
                        dirItem = new TreeItem<>(TreeEntry.directory(part));
                        parent.getChildren().add(dirItem);
                        dirMap.put(newPath, dirItem);
                    }
                    parent = dirItem;
                    currentPath = newPath;
                }
                parent.getChildren().add(new TreeItem<>(TreeEntry.leaf(entry)));
            }
        }

        sortTreeItems(root);

        TreeItem<TreeEntry> currentRoot = treeView.getRoot();
        currentRoot.getChildren().clear();
        currentRoot.getChildren().addAll(root.getChildren());
    }

    private void sortTreeItems(TreeItem<TreeEntry> item) {
        item.getChildren().sort((a, b) -> {
            TreeEntry ta = a.getValue();
            TreeEntry tb = b.getValue();
            if (ta.isDirectory() && !tb.isDirectory()) return -1;
            if (!ta.isDirectory() && tb.isDirectory()) return 1;
            return ta.getName().compareToIgnoreCase(tb.getName());
        });
        for (TreeItem<TreeEntry> child : item.getChildren()) {
            if (child.getValue().isDirectory()) {
                sortTreeItems(child);
            }
        }
    }

    private void filterTree(String query) {
        if (query == null || query.isBlank()) {
            buildTree(allEntries);
            return;
        }
        String lower = query.toLowerCase();
        List<PasswordEntry> filtered = allEntries.stream()
                .filter(e -> e.getFullPath().toLowerCase().contains(lower))
                .toList();
        buildTree(filtered);
        expandAll(treeView.getRoot());
    }

    private void expandAll(TreeItem<?> item) {
        if (item == null) return;
        item.setExpanded(true);
        for (TreeItem<?> child : item.getChildren()) {
            expandAll(child);
        }
    }

    private void setupSystemTray() {
        if ("wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE")) || !SystemTray.isSupported()) return;

        Platform.setImplicitExit(false);

        java.awt.EventQueue.invokeLater(() -> {
            javafx.scene.image.Image icon = FluxPassApp.getAppIcon();
            java.awt.Image trayImage;
            if (icon != null) {
                int w = (int) icon.getWidth();
                int h = (int) icon.getHeight();
                int[] pixels = new int[w * h];
                icon.getPixelReader().getPixels(0, 0, w, h,
                        javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, w);
                BufferedImage awt = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                awt.setRGB(0, 0, w, h, pixels, 0, w);
                trayImage = awt.getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH);
            } else {
                BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2d = image.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(new Color(33, 150, 243));
                g2d.fillOval(2, 0, 12, 12);
                g2d.setColor(new Color(255, 255, 255));
                g2d.setStroke(new BasicStroke(1.5f));
                g2d.drawLine(8, 8, 8, 14);
                g2d.drawLine(6, 12, 10, 12);
                g2d.drawLine(6, 13, 10, 13);
                g2d.dispose();
                trayImage = image;
            }

            trayIcon = new TrayIcon(trayImage, "FluxPass");
            trayIcon.setImageAutoSize(true);

            java.awt.PopupMenu popup = new java.awt.PopupMenu();

            java.awt.MenuItem showItem = new java.awt.MenuItem("Show");
            showItem.addActionListener(e -> Platform.runLater(() -> {
                stage.setIconified(false);
                stage.show();
                stage.toFront();
            }));

            java.awt.MenuItem exitItem = new java.awt.MenuItem("Exit");
            exitItem.addActionListener(e -> {
                clipboardExecutor.shutdownNow();
                Platform.runLater(() -> {
                    SystemTray.getSystemTray().remove(trayIcon);
                    Platform.exit();
                });
            });

            popup.add(showItem);
            popup.addSeparator();
            popup.add(exitItem);

            trayIcon.setPopupMenu(popup);

            trayIcon.addActionListener(e -> Platform.runLater(() -> {
                stage.setIconified(false);
                stage.show();
                stage.toFront();
            }));

            try {
                SystemTray.getSystemTray().add(trayIcon);
            } catch (AWTException ex) {
                trayIcon = null;
                System.err.println("Tray not available: " + ex.getMessage());
            }
        });
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message != null ? message : "Unknown error");
            alert.getDialogPane().getStylesheets().add(getClass().getResource(getThemeCss()).toExternalForm());
            alert.showAndWait();
        });
    }
}
