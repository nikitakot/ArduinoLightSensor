package sample;

import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.text.SimpleDateFormat;
import java.util.*;

public class Main extends Application {
    //seznam portu a port na arduino, zatim nullovy
    private SerialPort arduinoPort = null;
    private ObservableList<String> portList;
    private ComboBox comboBoxPorts;
    //gui prvky
    private LineChart<String, Number> lineChart;
    private XYChart.Series series;
    private Label labelValue;
    private Button buttonStop;
    //tlacitka na zobrazeni historie a cisteni db
    private Button clearDB;
    private Button historyButton;
    //bulean na zastaveni cteni z arduino proudu
    private boolean read = true;
    //database
    Database db;
    //prvky na kresleni near-real time grafu
    private ComboBox<String> comboBoxTime;
    Timer timer;
    volatile private List<XYChart.Data<String, Number>> seriesData;


    /**
     * vytvareni seznamu dostupnych portu
     */
    private void detectPort() {
        portList = FXCollections.observableArrayList();
        SerialPort[] serialPortNames = SerialPort.getCommPorts();
        for (SerialPort port : serialPortNames) {
            System.out.println(port.getSystemPortName());
            portList.add(port.getSystemPortName());
        }
    }

    @Override
    public void start(Stage primaryStage) {
        //label na zobrazeni aktualni hodnoty z arduino
        labelValue = new Label();
        labelValue.setFont(new Font("Arial", 28));
        //tlacitko na zastaveni
        buttonStop = new Button("Stop");
        buttonStop.setMaxSize(100, 20);
        buttonStop.setOnAction(event -> {
            read = false;
            try {
                timer.cancel();
                timer.purge();
            } catch (NullPointerException e) {
            }
        });
        buttonStop.setVisible(false);
        //inicializace tlacitka na historii
        historyButton = new Button("show history");
        historyButton.setOnAction(event -> showHistory());
        historyButton.setMaxSize(100, 20);
        //inicializace comboboxu s casem na near real time kresleni
        comboBoxTime = new ComboBox();
        comboBoxTime.getItems().addAll(
                "real-time",
                "10",
                "20",
                "30"
        );
        comboBoxTime.setMaxSize(100, 20);
        comboBoxTime.getSelectionModel().selectFirst();
        System.out.println(comboBoxTime.getValue());
        //inicializace linechartu
        final CategoryAxis xAxis = new CategoryAxis();
        final NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Time");
        yAxis.setLabel("Value");
        lineChart = new LineChart<String, Number>(xAxis, yAxis);
        lineChart.setTitle("Motion detector");
        series = new XYChart.Series();
        series.setName("detected values");
        lineChart.getData().add(series);
        //inicializace db
        db = new Database();
        //tlacitko na cisteni db
        clearDB = new Button("ClearDB");
        clearDB.setMaxSize(100, 20);
        clearDB.setOnAction(e -> db.clearDB());
        //vytvarime seznam dostupnych portu a vkladame to do comboboxu
        detectPort();
        comboBoxPorts = new ComboBox(portList);
        comboBoxPorts.valueProperty()
                .addListener(new ChangeListener<String>() {
                    @Override
                    public void changed(ObservableValue<? extends String> observable,
                                        String oldValue, String newValue) {
                        System.out.println(newValue);
                        connectArduino(newValue);
                    }
                });
        comboBoxPorts.setMaxSize(100, 20);
        //dalsi gui
        VBox vBox = new VBox();
        HBox hBox = new HBox();
        hBox.getChildren().addAll(comboBoxPorts, historyButton, comboBoxTime, clearDB, labelValue, buttonStop);
        vBox.getChildren().addAll(hBox, lineChart);

        StackPane root = new StackPane();
        root.getChildren().add(vBox);
        Scene scene = new Scene(root, 800, 400);

        primaryStage.setTitle("Arduino Motion Detector");
        primaryStage.setScene(scene);

        primaryStage.setOnCloseRequest(event -> {
            try {
                arduinoPort.closePort();
            } catch (NullPointerException e) {
                System.err.println("No port was assigned");
            }
            System.exit(0);
        });
        primaryStage.show();
    }

    /**
     * metoda inicializuje arduino port
     *
     * @param newValue nazev portu
     */
    private void connectArduino(String newValue) {
        arduinoPort = SerialPort.getCommPort(newValue);
        arduinoPort.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);
        //otevirame port
        arduinoPort.openPort();
        comboBoxTime.setDisable(true);
        buttonStop.setVisible(true);
        //thread na kresleni elementu na grafu
        new Thread() {
            @Override
            public void run() {
                try {
                    //stream na cteni z arduino
                    Scanner scanner = new Scanner(arduinoPort.getInputStream());
                    //inicializujeme timer na near real time graf
                    if (!comboBoxTime.getValue().equals("real-time")) {
                        seriesData = new ArrayList<>();
                        lineChart.setAnimated(false);
                        int timeTimer = Integer.parseInt(comboBoxTime.getValue());
                        timer = new Timer();
                        timer.scheduleAtFixedRate(new TimerTask() {
                            @Override
                            public void run() {
                                Platform.runLater(() -> {
                                    lineChart.getData().retainAll();
                                    series = new XYChart.Series();
                                    series.getData().addAll(seriesData);
                                    lineChart.getData().add(series);
                                    System.out.println("start");
                                    series.setName("detected values");
                                    seriesData.clear();
                                });
                            }
                        }, timeTimer * 1000, timeTimer * 1000);
                    }
                    //scitani radku ze streamu, kresleni do grafu, ukladani do db
                    while (scanner.hasNextLine() && read) {
                        try {
                            String line = scanner.nextLine();
                            int number = Integer.parseInt(line);
                            db.update(number);
                            String time = new SimpleDateFormat("HH:mm:ss").format(Calendar.getInstance().getTime());
                            System.out.println(number);
                            XYChart.Data<String, Number> a = new XYChart.Data<String, Number>();
                            a.setXValue(time);
                            a.setYValue(number);
                            Platform.runLater(() -> {
                                labelValue.setText(String.valueOf(number));
                                if (number == 1) {
                                    labelValue.setTextFill(Color.web("#ff3300"));
                                } else {
                                    labelValue.setTextFill(Color.web("#33cc33"));
                                }
                                if (comboBoxTime.getValue().equals("real-time"))
                                    series.getData().add(a);
                                else
                                    seriesData.add(a);
                            });
                        } catch (Exception e) {
                        }
                    }
                    scanner.close();
                } catch (NullPointerException e) {
                    System.err.println("Arduino isn't connected. Exception: " + e);
                }
            }
        }.start();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public void showHistory() {
        Stage historyStgge = new Stage();
        historyStgge.setTitle("Arduino History");
        final CategoryAxis xAxis = new CategoryAxis();
        final NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Time");
        yAxis.setLabel("Value");
        LineChart<String, Number> lineChart = new LineChart<String, Number>(xAxis, yAxis);
        XYChart.Series series = new XYChart.Series();
        series.setName("detected values");
        series.getData().addAll(db.getHistory());
        lineChart.getData().add(series);
        StackPane stackPane = new StackPane(lineChart);
        Scene scene = new Scene(stackPane, 400, 300);
        historyStgge.setScene(scene);
        historyStgge.show();
    }

}
