package sample;

/**
 * Created by kotn0 on 22.11.2016.
 */

import javafx.scene.chart.XYChart;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

//clasicke pripojeni k databazi, nic zajimaveho
public class Database {
    Connection con;
    Statement statement;

    public Database() {
        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
            con = DriverManager.getConnection("jdbc:oracle:thin:@localhost:1521:xe", "system", "Andrey050964");
            statement = con.createStatement();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    /**
     * automaticky inkrementuje ID pomoci sequence v DB(ID_INCREAMENT)
     * @param value hodnota na ukladani v db
     */
    public void update(int value) {
        String sql = "INSERT INTO ARDUINO_LOG VALUES (ID_INCREAMENT.NEXTVAL ," + value + ", CURRENT_TIMESTAMP)";
        try {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @return seriesData z DB
     */

    public List<XYChart.Data<String, Number>> getHistory() {
        String sql = "select * from ARDUINO_LOG";
        List<XYChart.Data<String, Number>> seriesData = new ArrayList<>();
        try {
            ResultSet resultSet = statement.executeQuery(sql);
            while (resultSet.next()) {
                XYChart.Data<String, Number> a = new XYChart.Data<String, Number>();
                Timestamp timestamp = resultSet.getTimestamp(3);
                String time = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(timestamp);
                a.setXValue(time);
                a.setYValue(resultSet.getInt(2));
                seriesData.add(a);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return seriesData;
    }

    /**
     * metoda cisti DB
     *
     */
    public void clearDB() {
        String sql = "TRUNCATE TABLE ARDUINO_LOG";
        String seqDrop = "DROP SEQUENCE \"SYSTEM\".\"ID_INCREAMENT\"";
        String seqCreate = "CREATE SEQUENCE  \"SYSTEM\".\"ID_INCREAMENT\"  START WITH 1 INCREMENT BY 1";
        try {
            statement.executeUpdate(sql);
            statement.executeUpdate(seqDrop);
            statement.executeUpdate(seqCreate);
            System.out.println("DB Cleared");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
