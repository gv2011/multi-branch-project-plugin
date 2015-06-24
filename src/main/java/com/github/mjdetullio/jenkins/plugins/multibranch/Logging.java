package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.util.Date;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.github.mjdetullio.jenkins.plugins.multibranch.impl.BranchesSynchronizerImpl;

public class Logging {
	
	private static final MyLogHandler handler = new MyLogHandler();

	public static void tweak() {
		final Logger root = Logger.getLogger("");
		root.addHandler(handler);
		root.log(Level.SEVERE, "1");
		final Logger global = Logger.getGlobal();
		root.log(Level.SEVERE, "2 "+global.getName()+".");
		global.log(Level.SEVERE, "3");
		Logger test = Logger.getLogger(BranchesSynchronizerImpl.class.getName());
		final int i = 4;
		while(test!=null){
			test.log(Level.SEVERE, Integer.toString(i));
			root.log(Level.SEVERE, test.getName()+";"+test.getLevel()+";"+test.getUseParentHandlers());
			test = test.getParent();
		}
	}

	public static void diagnose() {
		final StringBuilder sb = new StringBuilder();
		sb.append("\nDiagnose: "+new Date()+" "+Thread.currentThread()+"\n");
		final LogManager mgr = LogManager.getLogManager();
		handler.writeLn("LogManager: "+mgr.getClass());
		Logger test = Logger.getLogger(BranchesSynchronizerImpl.class.getName());
		while(test!=null){
			sb.append(test.getName()+"\t "+test.getUseParentHandlers()+"\t "+test.getLevel()+"\n");
			final Handler[] handlers = test.getHandlers();
			if(handlers!=null) for(final Handler h: handlers){
				sb.append("  Handler: "+h.getClass()+"\n");
			}
			test = test.getParent();
		}
		handler.writeLn(sb.toString());
	}

}
