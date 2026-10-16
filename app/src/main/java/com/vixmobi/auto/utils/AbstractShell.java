package com.vixmobi.auto.utils;

public abstract class AbstractShell {
    public static class Result {
        public int code = -1;
        public String error = "";
        public String result = "";
        public boolean timedOut;
        public boolean cancelled;
        public boolean truncated;

        @Override
        public String toString() {
            return "ShellResult{" +
                    "code=" + code +
                    ", error='" + error + '\'' +
                    ", result='" + result + '\'' +
                    '}';
        }
    }

    public static final String COMMAND_SU = "su";
    public static final String COMMAND_SH = "sh";
    public static final String COMMAND_EXIT = "exit\n";
    public static final String COMMAND_LINE_END = "\n";





}
