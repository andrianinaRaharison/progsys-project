package main;

import main.slave1.SlaveServer;

import java.io.IOException;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws  IOException {

        SlaveServer slave1 = new SlaveServer(6001, Paths.get("storage1"));
        SlaveServer slave2 = new SlaveServer(6002, Paths.get("storage2"));
        SlaveServer slave3 = new SlaveServer(6003, Paths.get("storage3"));
        SlaveServer slave4 = new SlaveServer(6004, Paths.get("storage4"));

        new Thread(() -> slave1.ecoute()).start();
        new Thread(() -> slave2.ecoute()).start();
        new Thread(() -> slave3.ecoute()).start();
        new Thread(() -> slave4.ecoute()).start();

        System.out.println("4 Slaves démarrés !");
    }

}
