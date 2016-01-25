package org.github.sipuada;

import com.google.common.eventbus.EventBus;

public class Sipuada {

	private static EventBus eventBus = new EventBus();
	
	public static EventBus getEventBus() {
		return eventBus;
	}

}
