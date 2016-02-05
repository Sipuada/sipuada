package org.github.sipuada.events;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import android.javax.sip.header.ContactHeader;

public class RegistrationSuccess {
	
	private final List<String> contactBindings = new LinkedList<>();
	
	public RegistrationSuccess(ListIterator<?> contactHeaders) {
		while (contactHeaders.hasNext()) {
			ContactHeader contactHeader = (ContactHeader) contactHeaders.next();
			contactBindings.add(contactHeader.getAddress().getURI().toString());
		}
	}

	public List<String> getContactBindings() {
		return contactBindings;
	}

}
