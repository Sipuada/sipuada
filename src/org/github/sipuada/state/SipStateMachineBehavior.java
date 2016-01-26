package org.github.sipuada.state;

import java.util.HashMap;
import java.util.Map;

import org.github.sipuada.RequestVerb;
import org.github.sipuada.State;
import org.github.sipuada.state.AbstractSipStateMachine.MessageDirection;

public class SipStateMachineBehavior {

	private static Map<State, Map<MessageDirection, Map<RequestVerb, Step>>> requestBehavior;
	private static Map<State, Map<MessageDirection, Map<Integer, Step>>> responseBehavior;
	{
		requestBehavior = new HashMap<>();
		for (State state : State.values()) {
			Map<MessageDirection, Map<RequestVerb, Step>> partialBehavior = new HashMap<>();
			partialBehavior.put(MessageDirection.INCOMING, new HashMap<RequestVerb, Step>());
			partialBehavior.put(MessageDirection.OUTGOING, new HashMap<RequestVerb, Step>());
			requestBehavior.put(state, partialBehavior);
		}
		responseBehavior = new HashMap<>();
		for (State state : State.values()) {
			Map<MessageDirection, Map<Integer, Step>> partialBehavior = new HashMap<>();
			partialBehavior.put(MessageDirection.INCOMING, new HashMap<Integer, Step>());
			partialBehavior.put(MessageDirection.OUTGOING, new HashMap<Integer, Step>());
			responseBehavior.put(state, partialBehavior);
		}
	}
	
	protected static class During {
		
		private final State currentState;
		
		public During(State current) {
			currentState = current;
		}
		
		public WhenRequest whenRequest(MessageDirection direction) {
			return new WhenRequest(currentState, direction);
		}
		
		public WhenResponse whenResponse(MessageDirection direction) {
			return new WhenResponse(currentState, direction);
		}
		
	}

	protected static class WhenRequest {

		private final State currentState;
		private final MessageDirection requestDirection;

		public WhenRequest(State current, MessageDirection direction) {
			currentState = current;
			requestDirection = direction;
		}
		
		public WithVerb with(RequestVerb verb) {
			return new WithVerb(currentState, requestDirection, verb);
		}

	}
	
	protected static class WhenResponse {

		private final State currentState;
		private final MessageDirection responseDirection;

		public WhenResponse(State current, MessageDirection direction) {
			currentState = current;
			responseDirection = direction;
		}
		
		public WithCode with(int code) {
			return new WithCode(currentState, responseDirection, code);
		}

	}
	
	protected static class WithVerb {

		private final State currentState;
		private final MessageDirection requestDirection;
		private final RequestVerb requestVerb;

		public WithVerb(State current, MessageDirection direction, RequestVerb verb) {
			currentState = current;
			requestDirection = direction;
			requestVerb = verb;
		}
		
		public Step goTo(State newState) {
			return new Step(currentState, requestDirection, requestVerb, newState);
		}

	}

	protected static class WithCode {

		private final State currentState;
		private final MessageDirection responseDirection;
		private final int responseCode;

		public WithCode(State current, MessageDirection direction, int code) {
			currentState = current;
			responseDirection = direction;
			responseCode = code;
		}
		
		public Step goTo(State newState) {
			return new Step(currentState, responseDirection, responseCode, newState);
		}

	}
	
	protected static class Step {

		private final State currentState;
		private final MessageDirection actionDirection;
		private final State newState;
		private RequestVerb requestVerb;
		private Integer responseCode;
		private boolean allowAction;
		private RequestVerb followUpRequestVerb;
		private Integer followUpResponseCode;
		
		public Step(State current, MessageDirection direction, RequestVerb verb, State brandnew) {
			this(current, direction, brandnew, verb, null, true, null,  null);
		}
		
		public Step(State current, MessageDirection direction, int code, State brandnew) {
			this(current, direction, brandnew, null, code, true, null,  null);
		}
		
		private Step(State current, MessageDirection direction, State brandnew, RequestVerb verb,
				Integer code, boolean allow, RequestVerb followUpRequest, Integer followUpResponse) {
			currentState = current;
			actionDirection = direction;
			newState = brandnew;
			requestVerb = verb;
			responseCode = code;
			allowAction = allow;
			followUpRequestVerb = followUpRequest;
			followUpResponseCode = followUpResponse;
			updateBehavior();
		}
		
		public Step andAllowAction() {
			allowAction = true;
			updateBehavior();
			return this;
		}
		
		public Step andDontAllowAction() {
			allowAction = false;
			updateBehavior();
			return this;
		}
		
		public Step thenSendRequest(RequestVerb followUpRequest) {
			followUpRequestVerb = followUpRequest;
			updateBehavior();
			return this;
		}

		public Step thenSendResponse(int followUpResponse) {
			followUpResponseCode = followUpResponse;
			updateBehavior();
			return this;
		}
		
		private void updateBehavior() {
			if (requestVerb != null) {
				requestBehavior.get(currentState).get(actionDirection).put(requestVerb, this);
			}
			else if (responseCode != null) {
				responseBehavior.get(currentState).get(actionDirection).put(responseCode, this);
			}
		}
		
		public boolean actionIsAllowed() {
			return allowAction;
		}
		
		public boolean hasFollowUpRequest() {
			return followUpRequestVerb != null;
		}
		
		public RequestVerb getFollowUpRequestVerb() {
			return followUpRequestVerb;
		}
		
		public boolean hasFollowUpResponse() {
			return followUpResponseCode != null;
		}
		
		public Integer getFollowUpResponseCode() {
			return followUpResponseCode;
		}

		public State getNextState() {
			return newState;
		}

	}
	
	public static During during(State currentState) {
		return new During(currentState);
	}
	
	public static Step computeNextStepAfterRequest(State currentState, MessageDirection direction, RequestVerb requestVerb) {
		return requestBehavior.get(currentState).get(direction).get(requestVerb);
	}
	
	public static Step computeNextStepAfterResponse(State currentState, MessageDirection direction, int responseCode) {
		return requestBehavior.get(currentState).get(direction).get(responseCode);
	}

}
