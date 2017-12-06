package com.ipcam.asyncio;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.util.Log;

public class AsyncExecutor<T>
{
	private final String TAG = "AsyncExecutor";
    private Queue<T> orders = null;
    private int capacity;
    private Lock lock = new ReentrantLock();
    private Condition notFull = lock.newCondition();
    private Condition notEmpty = lock.newCondition();   
    private ExecutingThread executingThread = null;
    private boolean bExitFlag = false;

    public AsyncExecutor()
    {
    	orders = new LinkedList<T>();
    	capacity = 100;
    	executingThread = new ExecutingThread();
    	executingThread.start();
    }
    public void executeAsync(T order)
    {
		lock.lock();

        try 
        {
            while(orders.size() == capacity)
            {
                notFull.await();
            }

            orders.add(order);
            notEmpty.signal();
        }
        catch (InterruptedException e)
        {
        	
        }
        lock.unlock();
    }
	public void stop()
	{
        if (executingThread != null)
        {
            lock.lock();
        	bExitFlag = true;

        	try
        	{
            	notEmpty.signal();
        	}
        	catch (Exception e)
        	{
        		Log.e(TAG, "AsyncExecutor: stop: caught an exception: " + e.toString());
        	}
            lock.unlock();
        }
	}
	private void waitAndExecuteOrder()
	{
		lock.lock();

        try
        {
            while((orders.isEmpty()) && !bExitFlag)
            {
                notEmpty.await();
            }
            if (!bExitFlag)
            {
	            T order = orders.remove();
	            executor(order);
	            notFull.signal();
            }
        }
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}

		lock.unlock();
	}
    public void executor(T order)
    {
        Log.e(TAG, "AsyncExecutor: executor: must be implemented in derived class");
    }
	class ExecutingThread extends Thread
	{
		@Override
		public void run()
		{
			while(!bExitFlag)
			{
                waitAndExecuteOrder();
			}		
		}
	}
}
