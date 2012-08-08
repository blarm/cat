package com.dianping.cat.consumer.dump;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;

import com.dianping.cat.configuration.NetworkInterfaceManager;
import com.dianping.cat.configuration.ServerConfigManager;
import com.dianping.cat.message.internal.MessageId;
import com.dianping.cat.message.spi.AbstractMessageAnalyzer;
import com.dianping.cat.message.spi.MessagePathBuilder;
import com.dianping.cat.message.spi.MessageTree;
import com.dianping.cat.storage.dump.MessageBucketManager;
import com.site.lookup.annotation.Inject;

public class DumpAnalyzer extends AbstractMessageAnalyzer<Object> implements Initializable, LogEnabled {
	@Inject
	private ServerConfigManager m_configManager;

	@Inject
	private MessagePathBuilder m_builder;

	@Inject
	private DumpChannelManager m_manager;

	@Inject
	private DumpUploader m_uploader;

	@Inject
	private MessageBucketManager m_bucketManager;

	public DumpUploader getDumpUploader() {
		return m_uploader;
	}

	private long m_extraTime;

	private long m_startTime;

	private long m_duration;

	private boolean m_localMode = true;

	private Logger m_logger;

	@Override
	public void doCheckpoint(boolean atEnd) {
		if (atEnd) {
			m_manager.closeAllChannels(m_startTime);
		}
	}

	@Override
	public void enableLogging(Logger logger) {
		m_logger = logger;
	}

	@Override
	public Set<String> getDomains() {
		return Collections.emptySet();
	}

	@Override
	public Object getReport(String domain) {
		throw new UnsupportedOperationException("This should not be called!");
	}

	@Override
	protected boolean isTimeout() {
		long currentTime = System.currentTimeMillis();
		long endTime = m_startTime + m_duration + m_extraTime;

		return currentTime > endTime;
	}

	@Override
	protected void process(MessageTree tree) {
		if (tree.getMessage() == null) {
			return;
		}

		String messageId = tree.getMessageId();
		MessageId id = MessageId.parse(messageId);

		if (id.getVersion() == 1) {
			if (!m_localMode) {
				try {
					String ipAddress = NetworkInterfaceManager.INSTANCE.getLocalHostAddress();
					long timestamp = tree.getMessage().getTimestamp();
					String domain = tree.getDomain();
					String path = m_builder.getMessagePath(domain + "-" + ipAddress, new Date(timestamp));
					DumpChannel channel = m_manager.openChannel(path, false, m_startTime);
					int length = channel.write(tree);

					if (length <= 0) {
						m_manager.closeChannel(channel);

						channel = m_manager.openChannel(path, true, m_startTime);
						channel.write(tree);
					}
				} catch (Exception e) {
					m_logger.error("Error when dumping to local file system!", e);
				}
			}
		} else {
			try {
				m_bucketManager.storeMessage(tree);
			} catch (IOException e) {
				m_logger.error("Error when dumping to local file system!", e);
			}
		}
	}

	public void setAnalyzerInfo(long startTime, long duration, long extraTime) {
		m_extraTime = extraTime;
		m_startTime = startTime;
		m_duration = duration;
	}

	@Override
	public void initialize() throws InitializationException {
		m_localMode = m_configManager.isLocalMode();

		if (!m_localMode) {
			m_uploader.start();
		}
	}
}
