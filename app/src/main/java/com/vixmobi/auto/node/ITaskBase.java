package com.vixmobi.auto.node;

public interface ITaskBase {
    String _getCurrentRunningPkg();
    String _getRunningActivity();
    public void _showLog();
    public void _hideLog();
    public void _fullScreenLog();
    public void _clearLog();
}
