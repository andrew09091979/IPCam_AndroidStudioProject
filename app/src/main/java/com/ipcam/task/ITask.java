package com.ipcam.task;

public interface ITask<T>
{
    public void performTask(T info);
    public void stop();
}
