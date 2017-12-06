package com.ipcam.task;

public interface ITaskChain
{
    public void addTask(ITask task);
    public ITask getNextTask();
    public int getID();
}
