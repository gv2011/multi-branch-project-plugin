package com.github.mjdetullio.jenkins.plugins.multibranch.util;

public interface Consumer<T> {

	    /**
	     * Performs this operation on the given argument.
	     *
	     * @param t the input argument
	     */
	    void accept(T t) throws Exception;
}
