package net.gmsworld.devicelocator.utilities;

import org.ocpsoft.prettytime.PrettyTime;

import java.util.Date;

public class TimeFormatter {
    private static PrettyTime pt;

    public static String format(Date date) {
        if (pt == null) {
            pt = new PrettyTime();
        }
        return pt.format(date);
    }

    public static String format(long timestamp) {
        return format(new Date(timestamp));
    }
}
