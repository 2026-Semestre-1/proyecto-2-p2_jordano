package simuladorminipc.fx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import simuladorminipc.fx.SimuladorController.DiskFileRow;
import simuladorminipc.fx.SimuladorController.DiskRow;
import simuladorminipc.fx.SimuladorController.MemoryRow;
import simuladorminipc.fx.SimuladorController.ProcessRow;
import java.io.File;
import java.util.List;

/**
 * JavaFX Application (View) for the OS Process Scheduling Simulator.
 *
 * <h3>Layout</h3>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  TOOLBAR: [Cargar] [▶ Auto] [▶₁ Proceso] [→ Paso] [⏹] [↺ Reset] │
 * ├──────────────┬──────────────────────────────┬────────────────────┤
 * │ LEFT         │ CENTER                       │ RIGHT              │
 * │  CPU / BCP   │  Colas en lista vertical     │  RAM + Disco       │
 * │  [divider]   │  NEW → LISTA → BLOQ          │  (pestañas)        │
 * │  Teclado     │  → TERMINADOS                │                    │
 * │  Terminal    │                              │                    │
 * └──────────────┴──────────────────────────────┴────────────────────┘
 * </pre>
 * All three panels are separated by draggable {@link SplitPane} dividers.
 */
public class SimuladorApp extends Application {

    // ── Controller ────────────────────────────────────────────────────────────

    private SimuladorController ctrl;

    /** Primary stage reference (needed for dialogs opened from inner panels). */
    private Stage primaryStage;

    // ── Toolbar controls ──────────────────────────────────────────────────────

    private Button   btnLoad, btnStart, btnProcAuto, btnStep, btnStop, btnReset, btnInfo;
    private Label    lblTick, lblAlgoStatus;

    // ── Keyboard input ────────────────────────────────────────────────────────

    private TextField tfKeyboard;
    private Button    btnKeyboard;
    private HBox      kbRow;

    // ── CPU panel ─────────────────────────────────────────────────────────────

    private Circle   cpuIndicator;
    private Label    lblCpuStatus, lblCpuProcess;

    // ── Event log ─────────────────────────────────────────────────────────────

    private ListView<String> logView;

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * JavaFX entry point. Constructs the full UI, wires the controller, and shows the primary stage.
     *
     * @param stage the primary {@link Stage} provided by the JavaFX runtime
     */
    @Override
    public void start(Stage stage) {
        ctrl = new SimuladorController();
        primaryStage = stage;

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e1e2e;");
        root.setPadding(new Insets(0));

        root.setTop(buildToolbar(stage));

        SplitPane mainSplit = new SplitPane(
            buildLeftPanel(),
            buildCenterPanel(),
            buildRightPanel()
        );
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.setDividerPositions(0.26, 0.73);
        mainSplit.setStyle("-fx-background-color:#1e1e2e;");
        root.setCenter(mainSplit);

        bindControls();

        Scene scene = new Scene(root, 1440, 860);
        stage.setTitle("Simulador de Gestion de Procesos de SO  —  Proyecto 1");
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setOnCloseRequest(e -> { ctrl.stopAuto(); Platform.exit(); });
        stage.show();
    }

    // =========================================================================
    // TOOLBAR
    // =========================================================================

    /**
     * Builds the top toolbar containing control buttons, algorithm selector,
     * quantum spinner, and tick counter.
     *
     * @param stage the primary stage (needed for the file-chooser dialog)
     * @return toolbar {@link Node}
     */
    private Node buildToolbar(Stage stage) {
        // Buttons
        btnLoad      = accentBtn("Cargar archivos",    "#3b82f6");
        btnStart     = accentBtn("▶  Auto-todos",       "#22c55e");
        btnProcAuto  = accentBtn("▶₁ Por proceso",       "#10b981");
        btnStep      = accentBtn("→  Paso",             "#f59e0b");
        btnStop      = accentBtn("⏹  Detener",          "#ef4444");
        btnReset     = accentBtn("↺  Reiniciar",        "#8b5cf6");
        btnInfo      = accentBtn("ℹ  Info",             "#0ea5e9");

        btnLoad.setOnAction(e -> doLoad(stage));
        btnStart.setOnAction(e -> ctrl.toggleAuto());
        btnProcAuto.setOnAction(e -> {
            if (ctrl.singleProcRunningProperty().get()) ctrl.stopSingleProcess();
            else ctrl.startSingleProcess();
        });
        btnStep.setOnAction(e -> ctrl.step());
        btnStop.setOnAction(e -> ctrl.stopAuto());
        btnReset.setOnAction(e -> doReset());
        btnInfo.setOnAction(e -> showInfoDialog(stage));

        // Tick indicator
        lblTick = new Label("Tick: 0");
        lblTick.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
        lblTick.setTextFill(Color.web("#facc15"));
        lblTick.textProperty().bind(
            Bindings.concat("Tick: ", ctrl.currentTickProperty().asString()));

        // Policy status badge (fixed FCFS — algorithm selection removed)
        lblAlgoStatus = new Label("FCFS");
        lblAlgoStatus.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
        lblAlgoStatus.setStyle("-fx-background-color:#4b5563; -fx-text-fill:#e2e8f0;"
                             + "-fx-padding:3 8 3 8; -fx-background-radius:10;");
        lblAlgoStatus.textProperty().bind(ctrl.policyLabelProperty());

        HBox bar = new HBox(10,
            btnLoad, sep(), btnStart, btnProcAuto, btnStep, btnStop, sep(), btnReset, btnInfo,
            sep(), lblTick, lblAlgoStatus);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.setStyle("-fx-background-color: #12122a; -fx-border-color:#2d2d44; -fx-border-width:0 0 2 0;");
        return bar;
    }

    // =========================================================================
    // LEFT PANEL – CPU BCP + Keyboard input + Terminal log
    // =========================================================================

    /**
     * Builds the left panel: CPU/BCP info (top, scrollable) separated by a
     * draggable divider from the keyboard input row and event-log terminal (bottom).
     *
     * @return left panel {@link Node}
     */
    private Node buildLeftPanel() {
        // ── BCP info box ──────────────────────────────────────────────────────
        VBox bcpBox = new VBox(8);
        bcpBox.setPadding(new Insets(14, 12, 10, 12));
        bcpBox.setStyle("-fx-background-color:#16213e;");

        cpuIndicator = new Circle(8, Color.web("#6b7280"));
        lblCpuStatus  = new Label("IDLE");
        lblCpuStatus.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
        lblCpuStatus.setTextFill(Color.web("#94a3b8"));
        lblCpuStatus.textProperty().bind(ctrl.cpuStatusProperty());

        lblCpuProcess = new Label("—");
        lblCpuProcess.setFont(Font.font("SansSerif", FontWeight.NORMAL, 11));
        lblCpuProcess.setTextFill(Color.web("#cbd5e1"));
        lblCpuProcess.textProperty().bind(ctrl.cpuProcessProperty());
        lblCpuProcess.setWrapText(true);

        HBox statusRow = new HBox(8, cpuIndicator, lblCpuStatus);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        ctrl.cpuStatusProperty().addListener((obs, o, n) -> {
            if ("CORRIENDO".equals(n)) {
                cpuIndicator.setFill(Color.web("#22c55e"));
                lblCpuStatus.setTextFill(Color.web("#4ade80"));
            } else {
                cpuIndicator.setFill(Color.web("#6b7280"));
                lblCpuStatus.setTextFill(Color.web("#94a3b8"));
            }
        });

        GridPane regs = new GridPane();
        regs.setHgap(8); regs.setVgap(5);
        addReg(regs, 0, "PC",   ctrl.regPCProperty());
        addReg(regs, 1, "IR",   ctrl.regIRProperty());
        addReg(regs, 2, "AC",   ctrl.regACProperty());
        addReg(regs, 3, "AX",   ctrl.regAXProperty());
        addReg(regs, 4, "BX",   ctrl.regBXProperty());
        addReg(regs, 5, "CX",   ctrl.regCXProperty());
        addReg(regs, 6, "DX",   ctrl.regDXProperty());
        addReg(regs, 7, "AH",   ctrl.regAHProperty());
        addReg(regs, 8, "AL",   ctrl.regALProperty());
        addReg(regs, 9, "Pila", ctrl.stackInfoProperty());

        Label burstLbl = new Label();
        burstLbl.setFont(Font.font("Monospaced", 11));
        burstLbl.setTextFill(Color.web("#94a3b8"));
        burstLbl.textProperty().bind(ctrl.cpuBurstInfoProperty());
        burstLbl.setWrapText(true);

        bcpBox.getChildren().addAll(
            sectionLabel("CPU 1 — BCP Activo"),
            statusRow, lblCpuProcess,
            hline(), regs, hline(), burstLbl
        );

        ScrollPane bcpScroll = new ScrollPane(bcpBox);
        bcpScroll.setFitToWidth(true);
        bcpScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bcpScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        bcpScroll.setStyle("-fx-background:#16213e; -fx-background-color:#16213e;");

        // ── Keyboard input row ────────────────────────────────────────────────
        tfKeyboard = new TextField();
        tfKeyboard.setPromptText("Valor 0-255  (Enter o Enviar)");
        tfKeyboard.setDisable(true);
        tfKeyboard.setStyle("-fx-background-color:#1a1a2e; -fx-text-fill:#4b5563;"
                          + "-fx-border-color:#2d2d44; -fx-border-radius:4; -fx-font-size:12px;");
        tfKeyboard.setOnAction(e -> doKeyboard());
        HBox.setHgrow(tfKeyboard, Priority.ALWAYS);

        btnKeyboard = accentBtn("\u23ce Enviar", "#f59e0b");
        btnKeyboard.setOnAction(e -> doKeyboard());
        btnKeyboard.setDisable(true);

        Label kbLbl = new Label("\u2328  Teclado:");
        kbLbl.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
        kbLbl.setTextFill(Color.web("#4b5563"));

        kbRow = new HBox(8, kbLbl, tfKeyboard, btnKeyboard);
        kbRow.setAlignment(Pos.CENTER_LEFT);
        kbRow.setPadding(new Insets(6, 10, 6, 10));
        kbRow.setStyle("-fx-background-color:#0d0d1a; -fx-border-color:#2d2d44;"
                     + "-fx-border-width:1 0 1 0;");

        // ── Event log / terminal ──────────────────────────────────────────────
        Label logHdr = sectionLabel("Terminal / Registro de Eventos");
        logView = new ListView<>(ctrl.getEventLog());
        logView.setStyle("-fx-background-color:#0a0a1a; -fx-border-color:#2d2d44;"
                       + "-fx-control-inner-background:#0a0a1a;");
        logView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(""); setStyle(""); return; }
                setText(item);
                setFont(Font.font("Monospaced", 11));
                if (item.startsWith("[ERR]"))       setTextFill(Color.web("#f87171"));
                else if (item.startsWith("[FIN]"))  setTextFill(Color.web("#4ade80"));
                else if (item.startsWith("[OUT]"))  setTextFill(Color.web("#67e8f9"));
                else if (item.startsWith("[KB]"))   setTextFill(Color.web("#fde68a"));
                else if (item.startsWith("[SIM]"))  setTextFill(Color.web("#a78bfa"));
                else                                setTextFill(Color.web("#94a3b8"));
                setStyle("-fx-background-color:transparent;");
            }
        });
        // Auto-scroll
        ctrl.getEventLog().addListener((ListChangeListener<String>) c ->
            Platform.runLater(() -> {
                int last = logView.getItems().size() - 1;
                if (last >= 0) logView.scrollTo(last);
            })
        );

        VBox.setVgrow(logView, Priority.ALWAYS);
        VBox logBox = new VBox(6, logHdr, logView);
        logBox.setPadding(new Insets(8, 12, 10, 12));
        logBox.setStyle("-fx-background-color:#0a0a1a;");
        VBox.setVgrow(logView, Priority.ALWAYS);

        // keyboard row sits above log, outside the scroll
        VBox bottomBox = new VBox(0, kbRow, logBox);
        VBox.setVgrow(logBox, Priority.ALWAYS);
        bottomBox.setStyle("-fx-background-color:#0a0a1a;");

        // ── Assemble vertical split: BCP (top) / keyboard+log (bottom) ────────
        SplitPane leftSplit = new SplitPane(bcpScroll, bottomBox);
        leftSplit.setOrientation(Orientation.VERTICAL);
        leftSplit.setDividerPositions(0.48);
        leftSplit.setPrefWidth(390);
        leftSplit.setMinWidth(300);
        leftSplit.setStyle("-fx-background-color:#16213e; -fx-border-color:#2d2d44;"
                         + "-fx-border-width:0 2 0 0;");
        SplitPane.setResizableWithParent(bcpScroll, true);
        SplitPane.setResizableWithParent(bottomBox,  true);
        return leftSplit;
    }

    /**
     * Adds a single CPU register row to a grid pane.
     *
     * @param grid grid to add the row to
     * @param row  0-based row index in the grid
     * @param name register display name
     * @param prop observable string property bound to the register value
     */
    private void addReg(GridPane grid, int row, String name, javafx.beans.property.StringProperty prop) {
        Label lbl = new Label(name + ":");
        lbl.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
        lbl.setTextFill(Color.web("#64748b"));
        lbl.setMinWidth(38);
        lbl.setAlignment(Pos.CENTER_RIGHT);

        Label val = new Label();
        val.setFont(Font.font("Monospaced", FontWeight.NORMAL, 12));
        val.setTextFill(Color.web("#e2e8f0"));
        val.textProperty().bind(prop);
        val.setStyle("-fx-background-color:#0f0f23; -fx-padding:2 8 2 8;"
                   + "-fx-background-radius:4; -fx-border-color:#2d2d44;"
                   + "-fx-border-radius:4;");
        val.setMinWidth(80);

        grid.add(lbl, 0, row);
        grid.add(val, 1, row);
    }

    // =========================================================================
    // CENTER PANEL – Process Queues
    // =========================================================================

    /**
     * Builds the center panel containing the four process queue sub-panels
     * (NEW, READY, BLOCKED, TERMINATED).
     *
     * @return center panel {@link Node}
     */
    private Node buildCenterPanel() {
        VBox nueva = queuePane("Nueva", ctrl.getNewQueueRows(), "#6366f1");
        VBox lista = queuePane("Lista", ctrl.getReadyQueueRows(), "#22c55e");
        VBox bloq = queuePane("Bloqueada", ctrl.getBlockedQueueRows(), "#f59e0b");
        VBox term = terminatedPane(ctrl.getTerminatedQueueRows());

        nueva.setPrefHeight(200);
        lista.setPrefHeight(220);
        bloq.setPrefHeight(220);
        term.setPrefHeight(260);

        VBox queues = new VBox(12, nueva, lista, bloq, term);
        queues.setPadding(new Insets(14, 12, 14, 12));
        queues.setFillWidth(true);
        queues.setStyle("-fx-background-color:#0f0f23;");

        ScrollPane scroll = new ScrollPane(queues);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.setStyle("-fx-background:#0f0f23; -fx-background-color:#0f0f23;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    /**
     * Builds a generic process queue pane (NEW, READY, or BLOCKED).
     *
     * @param title   section header text
     * @param items   observable list of {@link ProcessRow} items to display
     * @param columns column titles for the table
     * @return {@link VBox} containing the titled table
     */
    private VBox queuePane(String title, javafx.collections.ObservableList<ProcessRow> items,
                            String accentHex) {
        Label hdr = new Label(title.toUpperCase() + "  (0)");
        hdr.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        hdr.setTextFill(Color.web(accentHex));

        // Keep counter in the header
        items.addListener((ListChangeListener<ProcessRow>) c ->
            hdr.setText(title.toUpperCase() + "  (" + items.size() + ")")
        );

        TableView<ProcessRow> tbl = new TableView<>(items);
        tbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tbl.setStyle("-fx-background-color:#0f172a; -fx-table-cell-border-color:#1e293b;"
                   + "-fx-control-inner-background:#0f172a;");

        TableColumn<ProcessRow,Integer> cPid  = col("PID",      "pid");
        TableColumn<ProcessRow,String>  cName = col("Nombre",   "name");
        TableColumn<ProcessRow,String>  cSt   = col("Estado",   "state");
        TableColumn<ProcessRow,Integer> cPri  = col("P",        "priority");
        TableColumn<ProcessRow,Integer> cBurst= col("Burst",    "burst");
        TableColumn<ProcessRow,Integer> cRem  = col("Rem.",     "remaining");
        TableColumn<ProcessRow,Integer> cWait = col("Esp.",     "waiting");

        cPid.setMaxWidth(35);  cPri.setMaxWidth(28);
        cBurst.setMaxWidth(50); cRem.setMaxWidth(50); cWait.setMaxWidth(50);

        tbl.getColumns().addAll(cPid, cName, cSt, cPri, cBurst, cRem, cWait);

        // Colour rows by state
        tbl.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(ProcessRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    String colour = stateColour(item.getState());
                    setStyle("-fx-background-color:" + colour + ";");
                }
            }
        });

        styleTable(tbl);

        VBox pane = new VBox(6, hdr, tbl);
        pane.setPadding(new Insets(10));
        pane.setStyle("-fx-background-color:#0f172a; -fx-border-color:" + accentHex + ";"
                    + "-fx-border-width:0 0 0 3; -fx-border-radius:4;");
        VBox.setVgrow(tbl, Priority.ALWAYS);
        return pane;
    }

    /**
     * Builds the READY queue pane with an extra per-process statistics action.
     * Double-clicking a row (or clicking the "Stats" button column) opens a
     * dialog showing that process's start time, end time, and duration.
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    private VBox readyQueuePane(javafx.collections.ObservableList<ProcessRow> items) {
        String accentHex = "#22c55e";
        Label hdr = new Label("LISTA  (0)");
        hdr.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        hdr.setTextFill(Color.web(accentHex));

        items.addListener((ListChangeListener<ProcessRow>) c ->
            hdr.setText("LISTA  (" + items.size() + ")")
        );

        TableView<ProcessRow> tbl = new TableView<>(items);
        tbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tbl.setStyle("-fx-background-color:#0f172a; -fx-table-cell-border-color:#1e293b;"
                   + "-fx-control-inner-background:#0f172a;");

        TableColumn<ProcessRow,Integer> cPid   = col("PID",    "pid");
        TableColumn<ProcessRow,String>  cName  = col("Nombre", "name");
        TableColumn<ProcessRow,String>  cSt    = col("Estado", "state");
        TableColumn<ProcessRow,Integer> cPri   = col("P",      "priority");
        TableColumn<ProcessRow,Integer> cBurst = col("Burst",  "burst");
        TableColumn<ProcessRow,Integer> cRem   = col("Rem.",   "remaining");
        TableColumn<ProcessRow,Integer> cWait  = col("Esp.",   "waiting");

        // Stats action column: button that opens per-process timing dialog
        TableColumn<ProcessRow, Void> cStats = new TableColumn<>("Stats");
        cStats.setMaxWidth(52); cStats.setMinWidth(52);
        cStats.setSortable(false);
        cStats.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
            private final Button btn = new Button("Ver");
            {
                btn.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
                btn.setStyle("-fx-background-color:#22c55e; -fx-text-fill:black;"
                           + "-fx-background-radius:4; -fx-cursor:hand; -fx-padding:2 7 2 7;");
                btn.setOnAction(e -> {
                    ProcessRow row = getTableRow().getItem();
                    if (row != null) showProcessStatsDialog(row);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        cPid.setMaxWidth(35); cPri.setMaxWidth(28);
        cBurst.setMaxWidth(50); cRem.setMaxWidth(50); cWait.setMaxWidth(50);

        tbl.getColumns().addAll(cPid, cName, cSt, cPri, cBurst, cRem, cWait, cStats);

        // Double-click also opens the stats dialog
        tbl.setRowFactory(t -> {
            TableRow<ProcessRow> row = new TableRow<>() {
                @Override
                protected void updateItem(ProcessRow item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setStyle("");
                    } else {
                        String colour = stateColour(item.getState());
                        setStyle("-fx-background-color:" + colour + ";");
                    }
                }
            };
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    showProcessStatsDialog(row.getItem());
            });
            return row;
        });

        styleTable(tbl);

        VBox pane = new VBox(6, hdr, tbl);
        pane.setPadding(new Insets(10));
        pane.setStyle("-fx-background-color:#0f172a; -fx-border-color:" + accentHex + ";"
                    + "-fx-border-width:0 0 0 3; -fx-border-radius:4;");
        VBox.setVgrow(tbl, Priority.ALWAYS);
        return pane;
    }

    /**
     * Opens a small dialog showing per-process timing statistics for {@code row}.
     * Shows: process name, start time (HH:mm), end time (HH:mm), and duration in seconds.
     */
    private void showProcessStatsDialog(ProcessRow row) {
        Stage dlg = new Stage();
        if (primaryStage != null) {
            dlg.initOwner(primaryStage);
            dlg.initModality(javafx.stage.Modality.WINDOW_MODAL);
        }
        dlg.setTitle("Estadísticas — " + row.getName() + " (PID " + row.getPid() + ")");

        GridPane grid = new GridPane();
        grid.setHgap(20); grid.setVgap(10);
        grid.setPadding(new Insets(20, 24, 14, 24));
        grid.setStyle("-fx-background-color:#0d1117;");

        String durationStr = row.getDurationSec() >= 0
            ? String.format("%.2f seg", row.getDurationSec()) : "—";

        String[][] data = {
            {"Proceso:",        row.getName() + "  (PID " + row.getPid() + ")"},
            {"Estado:",         row.getState()},
            {"Hora de inicio:", row.getStartTimeHHmm()},
            {"Hora de fin:",    row.getEndTimeHHmm()},
            {"Duración:",       durationStr},
        };

        for (int i = 0; i < data.length; i++) {
            Label lbl = new Label(data[i][0]);
            lbl.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
            lbl.setTextFill(Color.web("#64748b"));

            Label val = new Label(data[i][1]);
            val.setFont(Font.font("Monospaced", FontWeight.BOLD, 13));
            val.setTextFill(Color.web("#e2e8f0"));
            val.setStyle("-fx-background-color:#1e293b; -fx-padding:3 10 3 10;"
                       + "-fx-background-radius:4;");
            val.setMinWidth(180);

            grid.add(lbl, 0, i);
            grid.add(val, 1, i);
        }

        Button btnClose = new Button("Cerrar");
        btnClose.setStyle("-fx-background-color:#22c55e; -fx-text-fill:black;"
                        + "-fx-font-weight:bold; -fx-padding:6 20 6 20; -fx-background-radius:6;");
        btnClose.setOnAction(e -> dlg.close());
        HBox footer = new HBox(btnClose);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(6, 24, 14, 24));
        footer.setStyle("-fx-background-color:#0d1117;");

        VBox root = new VBox(grid, footer);
        root.setStyle("-fx-background-color:#0d1117;");
        dlg.setScene(new Scene(root));
        dlg.setResizable(false);
        dlg.show();
    }

    /**
     * Builds the dedicated panel for TERMINATED processes.
     * Shows per-process statistics: arrival, service, waiting, turnaround,
     * response time, and the normalised turnaround ratio (TR/TS).
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    /**
     * Builds the terminated-process pane with a full individual-statistics table.
     * Columns: PID, Nombre, Prior., Llegada, Burst, TS, Espera, TR, Retorno, Ret/TS.
     *
     * @param items observable list of terminated {@link ProcessRow} items
     * @return {@link VBox} containing the statistics table
     */
    private VBox terminatedPane(javafx.collections.ObservableList<ProcessRow> items) {
        String accentHex = "#94a3b8";
        Label hdr = new Label("TERMINADA  (0)");
        hdr.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        hdr.setTextFill(Color.web(accentHex));

        items.addListener((ListChangeListener<ProcessRow>) c ->
            hdr.setText("TERMINADA  (" + items.size() + ")")
        );

        TableView<ProcessRow> tbl = new TableView<>(items);
        tbl.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tbl.setStyle("-fx-background-color:#0f172a; -fx-table-cell-border-color:#1e293b;"
                   + "-fx-control-inner-background:#0f172a;");

        // ── Column definitions ────────────────────────────────────────────────
        TableColumn<ProcessRow,Integer> cPid    = col("PID",      "pid");
        TableColumn<ProcessRow,String>  cName   = col("Nombre",   "name");
        TableColumn<ProcessRow,Integer> cPri    = col("Prior.",   "priority");
        // Individual statistics columns
        TableColumn<ProcessRow,Integer> cArr    = col("Llegada",  "arrivalTime");
        TableColumn<ProcessRow,Integer> cBurst  = col("Burst",    "burst");
        TableColumn<ProcessRow,Integer> cServ   = col("TS",       "serviceTime");
        TableColumn<ProcessRow,Integer> cWait   = col("Espera",   "waiting");
        TableColumn<ProcessRow,Integer> cResp   = col("TR",       "responseTime");
        TableColumn<ProcessRow,Integer> cTurn   = col("Retorno",  "turnaround");
        TableColumn<ProcessRow,String>  cRatio  = col("Ret/TS",   "turnaroundRatio");

        // Fixed widths to make all columns legible
        cPid  .setPrefWidth(36);  cPid  .setMinWidth(36);
        cPri  .setPrefWidth(52);  cPri  .setMinWidth(52);
        cArr  .setPrefWidth(60);  cArr  .setMinWidth(60);
        cBurst.setPrefWidth(46);  cBurst.setMinWidth(46);
        cServ .setPrefWidth(40);  cServ .setMinWidth(40);
        cWait .setPrefWidth(52);  cWait .setMinWidth(52);
        cResp .setPrefWidth(40);  cResp .setMinWidth(40);
        cTurn .setPrefWidth(55);  cTurn .setMinWidth(55);
        cRatio.setPrefWidth(55);  cRatio.setMinWidth(55);

        // Tooltip column headers so the abbreviations are self-explaining
        cArr  .setGraphic(colHeaderWithTip("Llegada",  "Tick en que lleg\u00f3 al sistema"));
        cServ .setGraphic(colHeaderWithTip("TS",       "Tiempo de Servicio: ticks reales de CPU"));
        cWait .setGraphic(colHeaderWithTip("Espera",   "Ticks en cola Listo sin ejecutar"));
        cResp .setGraphic(colHeaderWithTip("TR",       "Tiempo de Respuesta: ticks hasta 1\u00aa ejecuci\u00f3n"));
        cTurn .setGraphic(colHeaderWithTip("Retorno",  "Turnaround: desde llegada hasta fin"));
        cRatio.setGraphic(colHeaderWithTip("Ret/TS",   "Turnaround / Tiempo de Servicio (Retorno normalizado)"));

        // Wall-clock timing columns
        TableColumn<ProcessRow,String>  cStart = col("Inicio",  "startTimeHHmm");
        TableColumn<ProcessRow,String>  cEnd   = col("Fin",     "endTimeHHmm");
        TableColumn<ProcessRow,String>  cDur   = col("Dur(s)",  "durationSecStr");

        cStart.setPrefWidth(54); cStart.setMinWidth(54);
        cEnd  .setPrefWidth(48); cEnd  .setMinWidth(48);
        cDur  .setPrefWidth(60); cDur  .setMinWidth(60);

        cStart.setGraphic(colHeaderWithTip("Inicio", "Hora de inicio del proceso (HH:mm, reloj real)"));
        cEnd  .setGraphic(colHeaderWithTip("Fin",    "Hora de finalizaci\u00f3n del proceso (HH:mm, reloj real)"));
        cDur  .setGraphic(colHeaderWithTip("Dur(s)", "Duraci\u00f3n total en segundos (reloj real)"));

        tbl.getColumns().addAll(cPid, cName, cPri, cArr, cBurst, cServ, cWait, cResp, cTurn, cRatio, cStart, cEnd, cDur);

        tbl.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(ProcessRow item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(empty || item == null ? "" : "-fx-background-color:#1f2937;");
            }
        });

        styleTable(tbl);

        VBox pane = new VBox(6, hdr, tbl);
        pane.setPadding(new Insets(10));
        pane.setStyle("-fx-background-color:#0f172a; -fx-border-color:" + accentHex + ";"
                    + "-fx-border-width:0 0 0 3; -fx-border-radius:4;");
        VBox.setVgrow(tbl, Priority.ALWAYS);
        return pane;
    }

    /**
     * Returns a Label with tooltip suitable as a table-column graphic header.
     *
     * @param text    short column title
     * @param tipText tooltip shown on hover
     * @return styled Label node
     */
    /**
     * Creates a styled column header label with an attached tooltip.
     *
     * @param text    short column header text
     * @param tipText longer description shown in the tooltip
     * @return {@link Label} with tooltip set as graphic
     */
    private Label colHeaderWithTip(String text, String tipText) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
        lbl.setTextFill(Color.BLACK);
        javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip(tipText);
        javafx.scene.control.Tooltip.install(lbl, tip);
        return lbl;
    }

    /**
     * Returns a CSS colour string for a given process state display name.
     *
     * @param state process state label (e.g., "RUNNING", "READY")
     * @return hex colour string suitable for {@code -fx-text-fill}
     */
    private String stateColour(String state) {
        if (state == null) return "#0f172a";
        return switch (state) {
            case "Corriendo"  -> "#14532d";
            case "Lista"      -> "#1e3a5f";
            case "Bloqueada"  -> "#78350f";
            case "Terminado"  -> "#1f2937";
            default           -> "#0f172a";
        };
    }

    // =========================================================================
    // RIGHT PANEL – Memory Map (tabs: RAM + Disco)
    // =========================================================================

    /**
     * Builds the right panel containing the RAM address table and Disk file table.
     *
     * @return right panel {@link Node}
     */
    private Node buildRightPanel() {
        Tab ramTab  = new Tab("RAM",   buildRamPanel());
        Tab diskTab = new Tab("Disco", buildDiskPanel());
        ramTab.setClosable(false);
        diskTab.setClosable(false);

        TabPane tabs = new TabPane(ramTab, diskTab);
        tabs.setPrefWidth(370);
        tabs.setMinWidth(280);
        tabs.setTabMinWidth(60);
        tabs.setStyle("-fx-background-color:#0a0a1a; -fx-border-color:#2d2d44;"
                    + "-fx-border-width:0 0 0 2;");
        return tabs;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    /**
     * Builds the RAM memory-map table bound to the controller's RAM row list.
     *
     * @return {@link Node} containing the RAM panel
     */
    private Node buildRamPanel() {
        Label hdr = sectionLabel("Mapa de Memoria RAM");

        TableView<MemoryRow> tbl = new TableView<>(ctrl.getMemoryRows());
        tbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<MemoryRow,Integer> cAddr = col("Dir.",  "address");
        TableColumn<MemoryRow,String>  cZone = col("Zona",  "zone");
        TableColumn<MemoryRow,String>  cVal  = col("Valor", "value");

        cAddr.setMinWidth(42);  cAddr.setPrefWidth(46);  cAddr.setMaxWidth(55);
        cZone.setMinWidth(38);  cZone.setPrefWidth(42);  cZone.setMaxWidth(52);

        tbl.getColumns().addAll(cAddr, cZone, cVal);

        tbl.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(MemoryRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                if ("OS".equals(item.getZone())) {
                    setStyle("-fx-background-color:#1a1a3e; -fx-text-fill:#c7d2fe;");
                } else if ("IDX".equals(item.getZone())) {
                    setStyle("-fx-background-color:#1a2e1a; -fx-text-fill:#86efac;");
                } else if ("—".equals(item.getZone())) {
                    setStyle("-fx-background-color:#0f0f1e; -fx-text-fill:#475569;");
                } else {
                    setStyle("-fx-background-color:" + processColour(item.getZone()) + "; -fx-text-fill:#f8fafc;");
                }
            }
        });

        styleTable(tbl);
        tbl.setStyle("-fx-background-color:#0a0a1a; -fx-table-cell-border-color:#1e293b;"
                   + "-fx-control-inner-background:#0a0a1a;");

        VBox box = new VBox(10, hdr, tbl);
        box.setPadding(new Insets(14, 12, 14, 12));
        box.setStyle("-fx-background-color:#0a0a1a;");
        VBox.setVgrow(tbl, Priority.ALWAYS);
        return box;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    /**
     * Builds the Disk panel with two sections:
     * 1. File index summary (one row per stored file: name, start, size, preview).
     * 2. Raw disk cell map (address → zone → content).
     *
     * @return {@link Node} containing the disk panel
     */
    private Node buildDiskPanel() {
        // ── File index summary ────────────────────────────────────────────────
        Label idxHdr = new Label("Índice de Archivos en Disco");
        idxHdr.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        idxHdr.setTextFill(Color.web("#67e8f9"));
        idxHdr.setPadding(new Insets(0, 0, 4, 0));

        TableView<DiskFileRow> idxTbl = new TableView<>(ctrl.getDiskFileRows());
        idxTbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        idxTbl.setStyle("-fx-background-color:#0d1117; -fx-table-cell-border-color:#1e293b;"
                      + "-fx-control-inner-background:#0d1117;");

        TableColumn<DiskFileRow,String>  cFName  = col("Archivo",        "name");
        TableColumn<DiskFileRow,Integer> cFStart = col("Dir. inicio",    "startAddress");
        TableColumn<DiskFileRow,Integer> cFSize  = col("Instrucciones",  "size");
        TableColumn<DiskFileRow,String>  cFPrev  = col("Primera instrucción", "preview");

        cFStart.setMaxWidth(80);  cFStart.setMinWidth(70);
        cFSize.setMaxWidth(90);   cFSize.setMinWidth(80);

        cFStart.setGraphic(colHeaderWithTip("Dir. inicio", "Dirección del disco donde comienzan los datos del archivo"));
        cFSize .setGraphic(colHeaderWithTip("Instrucciones", "Número de instrucciones almacenadas en el archivo"));
        cFPrev .setGraphic(colHeaderWithTip("Primera instrucción", "Primera instrucción del archivo (vista previa)"));

        idxTbl.getColumns().addAll(cFName, cFStart, cFSize, cFPrev);

        idxTbl.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(DiskFileRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                String bg = (getIndex() % 2 == 0) ? "#111827" : "#0d1117";
                setStyle("-fx-background-color:" + bg + ";");
            }
        });
        styleTable(idxTbl);
        idxTbl.setFixedCellSize(26);
        idxTbl.prefHeightProperty().bind(
            idxTbl.fixedCellSizeProperty()
                  .multiply(Bindings.size(ctrl.getDiskFileRows()).add(1.1)));
        idxTbl.setMinHeight(52);
        idxTbl.setMaxHeight(200);

        // Legend labels
        HBox legend = new HBox(12,
            legendDot("#1a1a3e", "IDX (índice)"),
            legendDot("#14253d", "Archivo"),
            legendDot("#0f0f1e", "LIBRE"));
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.setPadding(new Insets(4, 0, 4, 0));

        VBox idxBox = new VBox(6, idxHdr, idxTbl, legend);
        idxBox.setPadding(new Insets(10, 12, 6, 12));
        idxBox.setStyle("-fx-background-color:#0a0a1a; -fx-border-color:#334155;"
                      + "-fx-border-width:0 0 2 0;");

        // ── Raw disk cell map ─────────────────────────────────────────────────
        Label hdr = sectionLabel("Mapa de Disco — celdas");

        TableView<DiskRow> tbl = new TableView<>(ctrl.getDiskRows());
        tbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<DiskRow,Integer> cAddr = col("Dir.",      "address");
        TableColumn<DiskRow,String>  cZone = col("Archivo",   "zone");
        TableColumn<DiskRow,String>  cVal  = col("Contenido", "value");

        cAddr.setMaxWidth(50);
        cZone.setMaxWidth(80);

        tbl.getColumns().addAll(cAddr, cZone, cVal);

        tbl.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(DiskRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                switch (item.getZone()) {
                    case "IDX"   -> setStyle("-fx-background-color:#1a1a3e;");
                    case "LIBRE" -> setStyle("-fx-background-color:#0f0f1e;");
                    default      -> setStyle("-fx-background-color:" + processColour(item.getZone()) + ";");
                }
            }
        });

        styleTable(tbl);
        tbl.setStyle("-fx-background-color:#0a0a1a; -fx-table-cell-border-color:#1e293b;"
                   + "-fx-control-inner-background:#0a0a1a;");

        VBox mapBox = new VBox(6, hdr, tbl);
        mapBox.setPadding(new Insets(10, 12, 10, 12));
        mapBox.setStyle("-fx-background-color:#0a0a1a;");
        VBox.setVgrow(tbl, Priority.ALWAYS);

        // ── Assemble with split so both sections are usable ───────────────────
        SplitPane diskSplit = new SplitPane(idxBox, mapBox);
        diskSplit.setOrientation(Orientation.VERTICAL);
        diskSplit.setDividerPositions(0.35);
        diskSplit.setStyle("-fx-background-color:#0a0a1a;");
        SplitPane.setResizableWithParent(idxBox, false);
        SplitPane.setResizableWithParent(mapBox,  true);
        return diskSplit;
    }

    /** Small coloured square + label for a legend entry. */
    private static HBox legendDot(String hexColor, String text) {
        javafx.scene.shape.Rectangle dot = new javafx.scene.shape.Rectangle(12, 12);
        dot.setFill(Color.web(hexColor));
        dot.setStyle("-fx-stroke:#334155; -fx-stroke-width:1;");
        Label lbl = new Label(text);
        lbl.setFont(Font.font("SansSerif", 10));
        lbl.setTextFill(Color.web("#94a3b8"));
        HBox box = new HBox(4, dot, lbl);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * Returns a CSS colour for a memory zone label (OS, FREE, or process name).
     *
     * @param zone zone description string
     * @return hex colour string
     */
    private String processColour(String zone) {
        // Simple deterministic colour per PID
        int hash = zone.hashCode() & 0xFF;
        String[] palette = {"#14253d","#1a2e1a","#2e1a1a","#2e2a0a","#1a1a2e","#2a1a2e"};
        return palette[hash % palette.length];
    }

    // =========================================================================
    // BINDINGS
    // =========================================================================

    /**
     * Wires all button, combo, and spinner event handlers to controller methods.
     */
    private void bindControls() {
        // Keyboard input: activates/deactivates on INT 09H
        ctrl.waitingKeyboardProperty().addListener((obs, o, n) -> {
            tfKeyboard.setDisable(!n);
            btnKeyboard.setDisable(!n);
            if (n) {
                kbRow.setStyle("-fx-background-color:#1a1200; -fx-border-color:#f59e0b;"
                             + "-fx-border-width:1 0 1 0;");
                tfKeyboard.setStyle("-fx-background-color:#2d2d44; -fx-text-fill:#fde68a;"
                                  + "-fx-border-color:#f59e0b; -fx-border-radius:4; -fx-font-size:12px;");
                tfKeyboard.clear();
                tfKeyboard.requestFocus();
            } else {
                kbRow.setStyle("-fx-background-color:#0d0d1a; -fx-border-color:#2d2d44;"
                             + "-fx-border-width:0 0 1 0;");
                tfKeyboard.setStyle("-fx-background-color:#1a1a2e; -fx-text-fill:#4b5563;"
                                  + "-fx-border-color:#2d2d44; -fx-border-radius:4; -fx-font-size:12px;");
            }
        });

        // Auto-run: toggle button label
        ctrl.autoRunningProperty().addListener((obs, o, n) ->
            btnStart.setText(n ? "⏸  Pausar auto" : "▶  Auto-todos")
        );

        // Single-process run: toggle label
        ctrl.singleProcRunningProperty().addListener((obs, o, n) ->
            btnProcAuto.setText(n ? "⏸  Pausar" : "▶₁ Por proceso")
        );

        // Disable buttons when finished; re-enable when new files are loaded
        ctrl.finishedProperty().addListener((obs, o, n) -> {
            if (n) {
                btnStart.setDisable(true);
                btnProcAuto.setDisable(true);
                btnStep.setDisable(true);
            } else {
                btnStart.setDisable(false);
                btnProcAuto.setDisable(false);
                btnStep.setDisable(false);
            }
        });

        // Re-enable on reset (handled in doReset())
    }

    // =========================================================================
    // ACTION HANDLERS
    // =========================================================================

    /**
     * Opens and displays the simulator Information dialog.
     * Contains instruction-set reference, syscall table, assembler grammar, memory layout,
     * and other usage information.
     *
     * @param owner primary stage (used to set dialog modality)
     */
    private void showInfoDialog(Stage owner) {
        Stage dlg = new Stage();
        dlg.initOwner(owner);
        dlg.initModality(javafx.stage.Modality.WINDOW_MODAL);
        dlg.setTitle("Referencia del Simulador");
        dlg.setMinWidth(980);
        dlg.setMinHeight(640);

        // ── Tab: BCP / PCB ────────────────────────────────────────────────────
        String[][] bcpData = {
            // Campo, Tipo/Rango, Descripción
            {"PID",             "entero ≥ 1",    "Identificador único del proceso; asignado automáticamente al crearlo."},
            {"Nombre",          "cadena",         "Nombre del archivo .asm cargado (sin extensión)."},
            {"Estado",          "NEW/READY/…",    "Estado actual en el diagrama de 5 estados."},
            {"Tiempo llegada",  "ticks",          "Tick del reloj en que el proceso fue admitido al sistema."},
            {"Burst time",      "ticks",          "Suma de los pesos de todas las instrucciones; tiempo total de CPU necesario."},
            {"Remaining time",  "ticks",          "Ticks de CPU que aún faltan por ejecutar (burst − ejecutado)."},
            {"Executed time",   "ticks",          "Ciclos de CPU que el proceso ya consumió efectivamente."},
            {"Prioridad",       "entero ≥ 1",    "Número de prioridad; menor valor = mayor prioridad (usado por Priority Scheduler)."},
            {"Quantum rem.",    "ticks",          "Ticks restantes en el cuanto actual (solo relevante en Round Robin)."},
            {"Waiting time",    "ticks",          "Tiempo total que el proceso pasó en la cola READY sin ejecutarse."},
            {"Turnaround time", "ticks",          "Tiempo total desde llegada hasta terminación."},
            {"Response time",   "ticks",          "Tiempo desde la llegada hasta la primera vez que obtuvo la CPU."},
            {"Memory base",     "dirección",      "Dirección física de inicio en RAM donde se cargó el proceso."},
            {"Memory limit",    "dirección",      "Última dirección física en RAM asignada al proceso (base + tamaño − 1)."},
            {"Memory required", "celdas",         "Número de celdas de RAM que el proceso necesita."},
            {"PC",              "índice",         "Program Counter: índice de la próxima instrucción a ejecutar."},
            {"IR",              "índice",         "Instruction Register: índice de la instrucción actualmente en ejecución."},
            {"AC",              "entero",         "Acumulador: registro central para todas las operaciones aritméticas."},
            {"AX",              "entero",         "Registro de propósito general A."},
            {"BX",              "entero",         "Registro de propósito general B."},
            {"CX",              "entero",         "Registro de propósito general C."},
            {"DX",              "entero / str",   "Registro de propósito general D; también porta datos de E/S y nombres de archivo en INT 21H."},
            {"AH",              "0x3C-0x4D",      "Sub-registro alto: código de subfunción para INT 21H (0x3C crear, 0x3D abrir, 0x40 escribir, 0x41 eliminar, 0x4D leer)."},
            {"AL",              "entero",         "Sub-registro bajo: contenido/dato para la operación INT 21H."},
            {"Pila (Stack)",    "5 slots",        "Pila de llamadas de tamaño fijo 5; usada por PUSH, POP y PARAM."},
            {"ZeroFlag",        "bool",           "Bandera de cero: activada por CMP cuando los operandos son iguales; leída por JE y JNE."},
        };

        GridPane bcpGrid = infoGrid(
            new String[]{"Campo BCP", "Tipo / Rango", "Descripci\u00f3n"},
            bcpData,
            new double[]{150, 130, 0});

        VBox bcpBox = new VBox(8, infoHdr("Bloque de Control de Proceso (BCP / PCB)"), bcpGrid);
        bcpBox.setPadding(new Insets(14));

        // ── Tab: Instrucciones ASM ────────────────────────────────────────────
        String[][] asmData = {
            // Instrucción, Sintaxis, Ciclos, Registros usados, Resultado / Efecto
            {"LOAD",    "LOAD reg",             "2",  "AC, reg",          "AC = reg"},
            {"STORE",   "STORE reg",            "2",  "AC, reg",          "reg = AC"},
            {"MOV",     "MOV dst, src|val",     "1",  "dst, src",         "dst = src  ó  dst = valor inmediato. Con MOV DX, \"nombre\" carga cadena en DX para INT 21H."},
            {"ADD",     "ADD reg",              "3",  "AC, reg",          "AC = AC + reg"},
            {"SUB",     "SUB reg",              "3",  "AC, reg",          "AC = AC − reg"},
            {"INC",     "INC [reg]",            "1",  "AC ó reg",         "AC++ ó reg++  (si no se especifica reg, opera sobre AC)."},
            {"DEC",     "DEC [reg]",            "1",  "AC ó reg",         "AC−− ó reg−−  (si no se especifica reg, opera sobre AC)."},
            {"SWAP",    "SWAP reg1, reg2",      "1",  "reg1, reg2",       "Intercambia los valores de reg1 y reg2."},
            {"INT 20H", "INT 20H",              "2",  "—",                "Finaliza el proceso (equivalente a sys_exit). El proceso pasa a TERMINATED."},
            {"INT 10H", "INT 10H",              "2",  "DX",               "Imprime el valor de DX en la pantalla/log. Si DX contiene una cadena (MOV DX,\"...\"), la imprime."},
            {"INT 09H", "INT 09H",              "3",  "DX",               "Solicita entrada del teclado. El proceso se BLOQUEA hasta que el usuario ingrese un valor (0–255); el resultado se deposita en DX."},
            {"INT 21H", "INT 21H",              "5",  "AH, AL, DX",       "Operaciones de archivo según AH:\n  0x3C → crear archivo con nombre DX\n  0x3D → leer archivo DX → resultado en AX\n  0x40 → escribir valor AL en archivo DX\n  0x41 → eliminar archivo DX\n  0x4D → leer contenido archivo DX → AX"},
            {"JMP",     "JMP +N | JMP -N",      "2",  "PC",               "Salto incondicional: PC = PC + 1 + offset. Offset positivo = adelante, negativo = atrás."},
            {"CMP",     "CMP reg1, reg2",       "2",  "reg1, reg2, ZeroFlag", "Compara reg1 con reg2. ZeroFlag = true si reg1 == reg2, false si no."},
            {"JE",      "JE +N | JE -N",        "2",  "PC, ZeroFlag",     "Salta si ZeroFlag está activo: PC = PC + 1 + offset. Si no, PC++."},
            {"JNE",     "JNE +N | JNE -N",      "2",  "PC, ZeroFlag",     "Salta si ZeroFlag está inactivo: PC = PC + 1 + offset. Si no, PC++."},
            {"PARAM",   "PARAM v1[,v2[,v3]]",   "3",  "Pila",             "Empuja hasta 3 valores inmediatos a la pila. Error si la pila supera 5 slots."},
            {"PUSH",    "PUSH reg",             "1",  "Pila, reg",        "Empuja el valor de reg al tope de la pila."},
            {"POP",     "POP reg",              "1",  "Pila, reg",        "Extrae el tope de la pila y lo deposita en reg."},
        };

        GridPane asmGrid = infoGrid(
            new String[]{"Instrucci\u00f3n", "Sintaxis", "Ciclos", "Registros usados", "Resultado / Efecto"},
            asmData,
            new double[]{80, 165, 48, 150, 0});

        VBox asmBox = new VBox(8, infoHdr("Instrucciones ASM del Simulador"), asmGrid);
        asmBox.setPadding(new Insets(14));

        // ── Tabs ─────────────────────────────────────────────────────────────
        Tab tabBcp = new Tab("BCP / PCB", new ScrollPane(bcpBox) {{
            setFitToWidth(true);
            setStyle("-fx-background-color:#0d1117; -fx-background:#0d1117;");
        }});
        Tab tabAsm = new Tab("Instrucciones ASM", new ScrollPane(asmBox) {{
            setFitToWidth(true);
            setStyle("-fx-background-color:#0d1117; -fx-background:#0d1117;");
        }});
        tabBcp.setClosable(false);
        tabAsm.setClosable(false);

        String[][] queueStatsData = {
            {"PID",    "entero",  "Identificador \u00fanico del proceso. Permite ubicar el mismo proceso en todas las colas, RAM y bit\u00e1cora."},
            {"Nombre", "cadena",  "Nombre base del archivo .asm asociado al proceso."},
            {"Estado", "texto",   "Estado actual visible del proceso en la cola correspondiente: NEW, LISTA, CORRIENDO, BLOQUEADA o TERMINADO."},
            {"P",      "entero",  "Prioridad del proceso. Un valor menor representa mayor prioridad cuando la pol\u00edtica la utiliza."},
            {"Burst",  "ticks",   "Tiempo total de CPU estimado para completar el programa; suma de los pesos de sus instrucciones."},
            {"Rem.",   "ticks",   "Tiempo restante de CPU. Indica cu\u00e1ntos ticks faltan para que el proceso termine. En BLOQUEADA tambi\u00e9n permite ver cu\u00e1nto trabajo le queda al proceso cuando regrese a LISTA."},
            {"Esp.",   "ticks",   "Tiempo acumulado esperando en la cola LISTA sin ejecutar. Sirve para detectar procesos rezagados."},
        };

        GridPane queueStatsGrid = infoGrid(
            new String[]{"Columna", "Unidad", "Descripci\u00f3n / Uso"},
            queueStatsData,
            new double[]{80, 70, 0});

        // ── Per-process statistics (TERMINATED queue columns) ──────────────────
        String[][] procStatsData = {
            {"Llegada",  "ticks",    "Tick del reloj en que el proceso fue admitido al sistema. Base para calcular Turnaround y Tiempo de Respuesta."},
            {"Burst",    "ticks",    "Suma de pesos de todas las instrucciones. Tiempo total estimado de CPU requerido por el proceso."},
            {"TS",       "ticks",    "Tiempo de Servicio real: ticks efectivos que la CPU dedic\u00f3 a ejecutar instrucciones del proceso."},
            {"Espera",   "ticks",    "Tiempo en cola LISTA sin ejecutarse. F\u00f3rmula: Espera = Retorno \u2212 TS."},
            {"TR",       "ticks",    "Tiempo de Respuesta: ticks desde la llegada hasta la primera asignaci\u00f3n de CPU. TR = primer tick de ejecuci\u00f3n \u2212 Llegada."},
            {"Retorno",  "ticks",    "Turnaround Time: tiempo total desde la llegada hasta la terminaci\u00f3n. Retorno = Tick fin \u2212 Llegada = TS + Espera."},
            {"Ret/TS",   "ratio",    "Turnaround normalizado: Retorno / TS. Indica cu\u00e1ntas veces m\u00e1s tard\u00f3 el proceso respecto a su TS puro. Valor ideal = 1.0."},
            {"Inicio",   "HH:mm",   "Hora del reloj real (wall clock) en que el proceso comenz\u00f3 a ejecutarse en la CPU por primera vez."},
            {"Fin",      "HH:mm",   "Hora del reloj real en que el proceso finaliz\u00f3 (pas\u00f3 a estado TERMINADO)."},
            {"Dur(s)",   "segundos", "Duraci\u00f3n real en segundos entre inicio y fin. C\u00e1lculo: (wallEndMillis \u2212 wallStartMillis) / 1000."},
        };

        GridPane procStatsGrid = infoGrid(
            new String[]{"Columna", "Unidad", "Descripci\u00f3n / F\u00f3rmula"},
            procStatsData,
            new double[]{80, 70, 0});

        VBox statsBox2 = new VBox(12,
            infoHdr("Columnas de Colas Activas (NEW / LISTA / BLOQUEADA / CPU)"),
            queueStatsGrid,
            infoHdr("Estad\u00edsticas por Proceso (Cola TERMINADA) \u2014 Columnas y F\u00f3rmulas"),
            procStatsGrid);
        statsBox2.setPadding(new Insets(14));

        Tab tabStats = new Tab("Estad\u00edsticas", new ScrollPane(statsBox2) {{
            setFitToWidth(true);
            setStyle("-fx-background-color:#0d1117; -fx-background:#0d1117;");
        }});
        tabStats.setClosable(false);

        TabPane tabs = new TabPane(tabBcp, tabAsm, tabStats);
        tabs.setStyle("-fx-background-color:#0d1117;");

        Button btnClose = new Button("Cerrar");
        btnClose.setStyle("-fx-background-color:#3b82f6; -fx-text-fill:white;"
                        + "-fx-font-weight:bold; -fx-padding:6 20 6 20; -fx-background-radius:6;");
        btnClose.setOnAction(e -> dlg.close());
        HBox footer = new HBox(btnClose);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(8, 14, 10, 14));
        footer.setStyle("-fx-background-color:#0d1117; -fx-border-color:#2d2d44; -fx-border-width:1 0 0 0;");

        BorderPane root = new BorderPane(tabs);
        root.setBottom(footer);
        root.setStyle("-fx-background-color:#0d1117;");

        dlg.setScene(new Scene(root, 980, 660));
        dlg.show();
    }

    /**
     * Builds an auto-sizing info grid (header row + data rows) using GridPane + Labels.
     * Column widths: fixed for all columns except the last (width=0), which expands
     * to fill the remaining space. All cells have wrapText so long descriptions
     * are never truncated.
     */
    private GridPane infoGrid(String[] headers, String[][] data, double[] colWidths) {
        GridPane grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);
        grid.setMaxWidth(Double.MAX_VALUE);

        for (int i = 0; i < colWidths.length; i++) {
            javafx.scene.layout.ColumnConstraints cc =
                new javafx.scene.layout.ColumnConstraints();
            if (colWidths[i] == 0) {
                // Last "fill" column
                cc.setHgrow(Priority.ALWAYS);
                cc.setMinWidth(200);
                cc.setFillWidth(true);
            } else {
                cc.setPrefWidth(colWidths[i]);
                cc.setMinWidth(colWidths[i]);
                cc.setHgrow(Priority.NEVER);
            }
            grid.getColumnConstraints().add(cc);
        }

        // Header row
        for (int c = 0; c < headers.length; c++) {
            Label lbl = new Label(headers[c]);
            lbl.setFont(Font.font("SansSerif", FontWeight.BOLD, 11));
            lbl.setTextFill(Color.web("#94a3b8"));
            lbl.setPadding(new Insets(6, 10, 6, 10));
            lbl.setMaxWidth(Double.MAX_VALUE);
            lbl.setStyle("-fx-background-color:#1e293b;"
                       + "-fx-border-color:#2d2d44;"
                       + "-fx-border-width:0 1 1 " + (c == 0 ? "1" : "0") + ";");
            GridPane.setFillWidth(lbl, true);
            grid.add(lbl, c, 0);
        }

        // Data rows
        for (int r = 0; r < data.length; r++) {
            String bg = (r % 2 == 0) ? "#111827" : "#0d1117";
            for (int c = 0; c < headers.length; c++) {
                String text = (c < data[r].length) ? data[r][c] : "";
                Label lbl = new Label(text);
                lbl.setWrapText(true);
                lbl.setFont(c == 0
                    ? Font.font("Monospaced", FontWeight.BOLD, 11)
                    : Font.font("Monospaced", 11));
                lbl.setTextFill(c == 0
                    ? Color.web("#67e8f9")
                    : Color.web("#e2e8f0"));
                lbl.setPadding(new Insets(5, 10, 5, 10));
                lbl.setMaxWidth(Double.MAX_VALUE);
                lbl.setStyle("-fx-background-color:" + bg + ";"
                           + "-fx-border-color:#1e293b;"
                           + "-fx-border-width:0 1 1 " + (c == 0 ? "1" : "0") + ";");
                GridPane.setFillWidth(lbl, true);
                grid.add(lbl, c, r + 1);
            }
        }

        grid.setStyle("-fx-background-color:#0d1117;");
        return grid;
    }

    /** @deprecated Use {@link #infoGrid} instead. */
    @Deprecated
    @SuppressWarnings("unused")
    private TableView<String[]> infoTable(String[] headers, String[][] data) {
        javafx.collections.ObservableList<String[]> rows =
            javafx.collections.FXCollections.observableArrayList(data);
        TableView<String[]> tbl = new TableView<>(rows);
        tbl.setEditable(false);

        for (int col = 0; col < headers.length; col++) {
            final int c = col;
            TableColumn<String[], String> tc = new TableColumn<>(headers[c]);
            tc.setCellValueFactory(param ->
                new javafx.beans.property.SimpleStringProperty(
                    param.getValue().length > c ? param.getValue()[c] : ""));
            tc.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setText(""); setStyle(""); return; }
                    setText(item);
                    setWrapText(true);
                    setFont(Font.font("Monospaced", 11));
                    setTextFill(Color.web("#e2e8f0"));
                    setStyle("-fx-background-color:transparent; -fx-padding:4 6 4 6;");
                }
            });
            if (c == 0) tc.setPrefWidth(120);
            else if (c == headers.length - 1) tc.setPrefWidth(340);
            else tc.setPrefWidth(130);
            tbl.getColumns().add(tc);
        }

        tbl.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(String[] item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setStyle("-fx-background-color:#0d1117;");
                else if (getIndex() % 2 == 0) setStyle("-fx-background-color:#111827;");
                else                          setStyle("-fx-background-color:#0d1117;");
            }
        });

        styleTable(tbl);
        tbl.setStyle("-fx-background-color:#0d1117; -fx-table-cell-border-color:#1e293b;"
                   + "-fx-control-inner-background:#0d1117;");
        tbl.setPrefHeight(400);
        return tbl;
    }

    private Label infoHdr(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        l.setTextFill(Color.web("#67e8f9"));
        l.setPadding(new Insets(0, 0, 4, 0));
        return l;
    }

    /**
     * Handles the "Load Files" button: opens a multi-file chooser for {@code .asm} files
     * and forwards them to the controller for assembly and loading.
     *
     * @param stage the primary stage (parent for the file chooser dialog)
     */
    private void doLoad(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar archivo(s) .asm");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Archivos ASM (*.asm)", "*.asm"));
        fc.setInitialDirectory(new File(System.getProperty("user.dir")));
        List<File> files = fc.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) return;

        List<String> errors = ctrl.loadFiles(files);
        if (!errors.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                String.join("\n", errors), ButtonType.OK);
            alert.setHeaderText("Errores de sintaxis en archivos ASM");
            alert.setTitle("Advertencia");
            alert.showAndWait();
        }

        btnStart.setDisable(false);
        btnStep.setDisable(false);
        ctrl.finishedProperty().set(false);
    }

    /**
     * Handles the "Reset" button: stops automation, resets the controller, and refreshes the UI.
     */
    private void doReset() {
        ctrl.reset();
        btnStart.setDisable(false);
        btnProcAuto.setDisable(false);
        btnStep.setDisable(false);
        btnStart.setText("▶  Auto-todos");
        btnProcAuto.setText("▶₁ Por proceso");
    }

    /**
     * Handles the keyboard input button: reads the text field and delivers
     * the value to the controller as a keyboard interrupt reply.
     */
    private void doKeyboard() {
        String text = tfKeyboard.getText();
        if (!ctrl.provideKeyboardInput(text)) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                "Ingrese un numero entero entre 0 y 255.", ButtonType.OK);
            alert.setHeaderText("Entrada invalida");
            alert.showAndWait();
        } else {
            tfKeyboard.clear();
        }
    }

    // =========================================================================
    // UTILITY / FACTORY METHODS
    // =========================================================================

    /** Accent-coloured button with BLACK text for readability. */
    /**
     * Creates a styled accent button.
     *
     * @param text  button label
     * @param bgHex background colour in hex (e.g. {@code "#3b82f6"})
     * @return configured {@link Button}
     */
    private static Button accentBtn(String text, String bgHex) {
        Button b = new Button(text);
        b.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        b.setTextFill(Color.BLACK);
        b.setStyle("-fx-background-color:" + bgHex + ";"
                 + "-fx-text-fill: black;"
                 + "-fx-background-radius:6;"
                 + "-fx-cursor:hand;"
                 + "-fx-padding:7 14 7 14;");
        b.setOnMouseEntered(e -> b.setStyle(
            "-fx-background-color:derive(" + bgHex + ",20%);"
          + "-fx-text-fill: black;"
          + "-fx-background-radius:6; -fx-cursor:hand; -fx-padding:7 14 7 14;"));
        b.setOnMouseExited(e -> b.setStyle(
            "-fx-background-color:" + bgHex + ";"
          + "-fx-text-fill: black;"
          + "-fx-background-radius:6; -fx-cursor:hand; -fx-padding:7 14 7 14;"));
        return b;
    }

    /**
     * Creates a styled toolbar label.
     *
     * @param text label text
     * @return styled {@link Label}
     */
    private static Label toolLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
        l.setTextFill(Color.web("#94a3b8"));
        return l;
    }

    /**
     * Creates a styled section header label.
     *
     * @param text header text
     * @return styled {@link Label}
     */
    private static Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("SansSerif", FontWeight.BOLD, 13));
        l.setTextFill(Color.web("#e2e8f0"));
        return l;
    }

    /**
     * Creates a lightweight vertical separator for the toolbar.
     *
     * @return {@link Separator} node
     */
    private static Separator sep() {
        Separator s = new Separator(Orientation.VERTICAL);
        s.setPadding(new Insets(0, 4, 0, 4));
        s.setStyle("-fx-background-color:#4b5563;");
        return s;
    }

    /**
     * Creates a styled horizontal divider line (as a {@link Region}).
     *
     * @return horizontal rule {@link Node}
     */
    private static Node hline() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color:#2d2d44;");
        return s;
    }

    /** Generic table column factory using PropertyValueFactory. */
    /**
     * Creates a table column bound to a JavaFX bean property.
     *
     * @param <S>      table row type
     * @param <T>      cell value type
     * @param title    column header text
     * @param property name of the property on the row type
     * @return configured {@link TableColumn}
     */
    private static <S, T> TableColumn<S, T> col(String title, String property) {
        TableColumn<S, T> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(property));
        return c;
    }

    /**
     * Applies the common dark-theme style to a table view.
     *
     * @param tbl the table view to style
     */
    private static void styleTable(TableView<?> tbl) {
        tbl.setPlaceholder(new Label("(vacía)") {{
            setTextFill(Color.web("#64748b"));
        }});
        tbl.getStyleClass().add("dark-table");
        Runnable applyHeaderStyle = () -> {
            Node headerBg = tbl.lookup(".column-header-background");
            if (headerBg != null) {
                headerBg.setStyle("-fx-background-color:#1e293b; -fx-border-color:#334155; -fx-border-width:0 0 1 0;");
            }
            java.util.Set<Node> headerLabels = tbl.lookupAll(".column-header .label");
            for (Node headerLabel : headerLabels) {
                headerLabel.setStyle("-fx-text-fill:black; -fx-font-weight:bold;");
            }
        };
        tbl.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) Platform.runLater(applyHeaderStyle);
        });
        tbl.widthProperty().addListener((obs, o, n) -> Platform.runLater(applyHeaderStyle));
    }

    // =========================================================================
    // MAIN
    // =========================================================================

    /**
     * JavaFX application main entry point.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(String[] args) {
        launch(args);
    }
}
