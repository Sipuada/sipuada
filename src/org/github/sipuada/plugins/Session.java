package org.github.sipuada.plugins;

public class Session {

	private final String localDataAddress;
	private final int localDataPort;
	private final String localControlAddress;
	private final int localControlPort;
	private final String remoteDataAddress;
	private final int remoteDataPort;
	private final String remoteControlAddress;
	private final int remoteControlPort;
	private MediaDirection direction;
	private Object payload;

	public Session(String localDataAddress, int localDataPort,
			String localControlAddress, int localControlPort,
			String remoteDataAddress, int remoteDataPort,
			String remoteControlAddress, int remoteControlPort,
			MediaDirection direction) {
		super();
		this.localDataAddress = localDataAddress;
		this.localDataPort = localDataPort;
		this.localControlAddress = localControlAddress;
		this.localControlPort = localControlPort;
		this.remoteDataAddress = remoteDataAddress;
		this.remoteDataPort = remoteDataPort;
		this.remoteControlAddress = remoteControlAddress;
		this.remoteControlPort = remoteControlPort;
		this.direction = direction;
	}

	public String getLocalDataAddress() {
		return localDataAddress;
	}

	public int getLocalDataPort() {
		return localDataPort;
	}

	public String getLocalControlAddress() {
		return localControlAddress;
	}

	public int getLocalControlPort() {
		return localControlPort;
	}

	public String getRemoteDataAddress() {
		return remoteDataAddress;
	}

	public int getRemoteDataPort() {
		return remoteDataPort;
	}

	public String getRemoteControlAddress() {
		return remoteControlAddress;
	}

	public int getRemoteControlPort() {
		return remoteControlPort;
	}

	public MediaDirection getDirection() {
		return direction;
	}

	public void setDirection(MediaDirection direction) {
		this.direction = direction;
	}

	public Object getPayload() {
		return payload;
	}

	public void setPayload(Object payload) {
		this.payload = payload;
	}

}
