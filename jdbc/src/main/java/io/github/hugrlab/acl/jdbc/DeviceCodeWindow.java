package io.github.hugrlab.acl.jdbc;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** The device code in a small window that closes itself when the sign-in completes or fails. */
final class DeviceCodeWindow {
	private DeviceCodeWindow() {
	}

	static AutoCloseable show(String userCode, String verificationUri, String link, Interaction interaction) {
		JFrame[] frame = new JFrame[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				JFrame f = new JFrame("duckdb-acl sign-in");
				JPanel panel = new JPanel(new BorderLayout(8, 8));
				panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
				panel.add(new JLabel("<html>Open <b>" + verificationUri + "</b> and enter this code:</html>"),
				    BorderLayout.NORTH);
				JLabel code = new JLabel(userCode, SwingConstants.CENTER);
				code.setFont(code.getFont().deriveFont(Font.BOLD, 28f));
				panel.add(code, BorderLayout.CENTER);
				JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
				JButton open = new JButton("Open browser");
				open.addActionListener(e -> {
					try {
						interaction.openBrowser(URI.create(link));
					} catch (Exception ignored) {
						// the address is on screen either way
					}
				});
				buttons.add(open);
				panel.add(buttons, BorderLayout.SOUTH);
				f.setContentPane(panel);
				f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				f.pack();
				f.setLocationRelativeTo(null);
				f.setAlwaysOnTop(true);
				f.setVisible(true);
				frame[0] = f;
			});
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (InvocationTargetException e) {
			// no window: the code went to stderr
		}
		return () -> {
			if (frame[0] != null) {
				SwingUtilities.invokeLater(frame[0]::dispose);
			}
		};
	}
}
