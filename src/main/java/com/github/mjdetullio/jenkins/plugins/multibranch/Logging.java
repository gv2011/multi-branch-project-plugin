package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.mjdetullio.jenkins.plugins.multibranch.impl.BranchesSynchronizerImpl;

public class Logging {

	public static void tweak() {
		final Logger root = Logger.getLogger("");
		final Handler handler = new MyLogHandler();
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

}
