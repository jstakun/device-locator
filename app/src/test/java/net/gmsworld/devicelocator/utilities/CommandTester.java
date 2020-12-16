package net.gmsworld.devicelocator.utilities;

import android.os.Bundle;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class CommandTester {

    private String message = "Aboutdlapp12345";
    private String pin = "1234";

    private String[] messages2 = {"Aboutdlapp12345", "Abdlapp12345", "Aboutdlapp1234", "Abdlapp1234"};

    private String[] messages3 = {"Aboutdl12345", "Abdl12345", "Aboutdl1234", "Abdl1234", "Aboutdl 12345", "Abdl 12345", "Aboutdl 1234", "Abdl 1234"};

    @Test
    public void test() {
        for (AbstractCommand c : Command.getCommands()) {
            System.out.println("Matching " + message + " with " + c.getSmsCommand() + "app and " + pin);
            int foundCommand = c.findAppCommand(null, StringUtils.trim(message), null, null, new Bundle(), pin, true);
            System.out.println(foundCommand + "");
        }
    }

    @Test
    public void test2() {
        for (String message : messages2) {
            AbstractCommand c = Command.getCommandByName("aboutdl");
            System.out.println("Matching " + message + " with " + c.getSmsCommand() + "app and " + pin);
            int foundCommand = c.findAppCommand(null, StringUtils.trim(message), null, null, new Bundle(), pin, true);
            System.out.println(foundCommand + "");
        }
    }

    @Test
    public void test3() {
        for (String message : messages3) {
            AbstractCommand c = Command.getCommandByName("aboutdl");
            System.out.println("Matching " + message + " with " + c.getSmsCommand() + " and " + pin);
            boolean foundCommand = c.findSmsCommand(null, StringUtils.trim(message), null, pin, true, false);
            System.out.println(foundCommand + "");
        }
    }
}
