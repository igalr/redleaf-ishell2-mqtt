package ca.redleafsolutions.ishell.mqtt;

import ca.redleafsolutions.base.events.Event;

public class QEvent implements Event {
	private MQTT client;

	public QEvent (MQTT client) {
		this.client = client;
	}

	public MQTT getClient () {
		return client;
	}

	public static class MessageReceived extends QEvent {
		private String topic;
		private byte[] payload;

		public MessageReceived(MQTT client, String topic, byte[] payload) {
			super (client);
			this.topic = topic;
			this.payload = payload;
		}
		
		public String getTopic () {
			return topic;
		}

		public byte[] getPayload () {
			return payload;
		}
	}

	public static class MessageSent extends QEvent {
		private int messageId;

		public MessageSent (MQTT client, int messageId) {
			super (client);
			this.messageId = messageId;
		}
		
		public int getMessageId () {
			return messageId;
		}
	}

	public static class ConnectionLost extends QEvent {
		private Throwable e;

		public ConnectionLost (MQTT client, Throwable e) {
			super (client);
			this.e = e;
		}
		
		public Throwable getEexception () {
			return e;
		}
	}
}
