package com.ipcam.task;

import java.util.LinkedList;
import java.util.Queue;

public class TaskChainImpl implements ITaskChain
{
    private Queue<ITask> tasks = null;
    
    public TaskChainImpl()
    {
    	tasks = new LinkedList<ITask>();
    }
	@Override
	public void addTask(ITask task)
	{
		tasks.add(task);
	}

	@Override
	public ITask getNextTask()
	{
		return null;
	}

	@Override
	public int getID() 
	{
		return 0;
	}

}
