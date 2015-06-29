package com.github.mjdetullio.jenkins.plugins.multibranch.util;

import static com.github.mjdetullio.jenkins.plugins.multibranch.util.FormattingUtils.format;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiagnosticLock implements Lock{
	
	private static final Logger LOG = LoggerFactory.getLogger(DiagnosticLock.class);
	
	private static final Timer TIMER = new Timer(DiagnosticLock.class.getName(), true);
	private final String name;
	private final Duration lockTimeout;
	private final ReentrantLock lock = new ReentrantLock();
	private TimerTask warnTask;

	public DiagnosticLock(final String name, final Duration lockTimeout) {
		this.name = name;
		this.lockTimeout = lockTimeout;
	}

	@Override
	public void lock() {
		boolean success = false;
		try {
			success = lock.tryLock(lockTimeout.toMillis(), TimeUnit.MILLISECONDS);
			if(!success) throw new IllegalStateException(format("{} is still locked after waiting {}.", this, lockTimeout));
			assert lock.isHeldByCurrentThread();
			success = false;
			try{
				beforeActions();
				success = true;
			} finally{
				if(!success) lock.unlock();
			}
		} catch (final InterruptedException e) {
			throw new RuntimeException(
					format("{} has been interrupted while waiting for {}.", Thread.currentThread(), this), e);
		}
	}

	@Override
	public void unlock() {
		try{
			afterActions();
		} finally{
			lock.unlock();
		}
	}

	private void beforeActions() {
		final Date start = new Date();
		warnTask = new TimerTask(){
			@Override
			public void run() {
				LOG.warn("{} is locked by {} for {} now.", this, Thread.currentThread(), Duration.since(start));					
			}};
		TIMER.schedule(warnTask, lockTimeout.toMillis()/3, TimeUnit.SECONDS.toMillis(5));
	}

	private void afterActions() {
		warnTask.cancel();		
	}

	@Override
	public String toString() {
		return name;
	}
	
	public void checkLocked() {
		assert lock.isHeldByCurrentThread();
	}


	@Override
	public void lockInterruptibly() throws InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean tryLock() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean tryLock(final long time, final TimeUnit unit){
		throw new UnsupportedOperationException();
	}

	@Override
	public Condition newCondition() {
		throw new UnsupportedOperationException();
	}

}
