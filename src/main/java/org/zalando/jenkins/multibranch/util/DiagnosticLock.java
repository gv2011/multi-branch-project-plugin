/*
 * The MIT License
 *
 * Copyright (c) 2015, Zalando SE
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.zalando.jenkins.multibranch.util;

import static org.zalando.jenkins.multibranch.util.FormattingUtils.format;

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
		LOG.info("Created {} with name {} and timeout of {}.", DiagnosticLock.class, name, lockTimeout);
	}

	@Override
	public void lock() {
		try {
			final boolean aquired = lock.tryLock(lockTimeout.toMillis(), TimeUnit.MILLISECONDS);
			if(!aquired) throw new IllegalStateException(format("{} is still locked after waiting {}.", this, lockTimeout));
			assert lock.isHeldByCurrentThread();
			boolean success = false;
			try{
				if(lock.getHoldCount()==1){
				  LOG.debug("{} is now held by {}.", this, Thread.currentThread());
				  beforeActions();
				}
				success = true;
			} finally{
				//In case of any exceptions after obtaining the lock, release it again:
				if(!success){
					lock.unlock();
					if(lock.getHoldCount()==0) LOG.debug("{} has been released by {}.", this, Thread.currentThread());
				}
			}
		} catch (final InterruptedException e) {
			throw new RuntimeException(
					format("{} has been interrupted while waiting for {}.", Thread.currentThread(), this), e);
		}
	}

	@Override
	public void unlock() {
		try{
			if(lock.getHoldCount()==1) afterActions();
		} finally{
			lock.unlock();
		}
	}

	private void beforeActions() {
		final Date start = new Date();
		final Thread thread = Thread.currentThread();
		warnTask = new TimerTask(){
			@Override
			public void run() {
				final Duration lockDuration = Duration.since(start);
				LOG.warn("{} is locked by {} for {} now.", DiagnosticLock.this, thread, lockDuration);
				if(LOG.isDebugEnabled()){
					if(lockDuration.compareTo(Duration.of(1, TimeUnit.MINUTES))>0){
						final StringBuilder sb = new StringBuilder("Current stack trace:\n");
						for(final StackTraceElement e: thread.getStackTrace()){
							sb.append("  ").append(e).append('\n');
						}
						LOG.debug(sb.toString());
					}
				}
				thread.getStackTrace();
			}};
		TIMER.schedule(warnTask, lockTimeout.toMillis()/3, TimeUnit.SECONDS.toMillis(5));
	}

	private void afterActions() {
		warnTask.cancel();
		LOG.debug("{} will be released by {} now.", this, Thread.currentThread());
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
