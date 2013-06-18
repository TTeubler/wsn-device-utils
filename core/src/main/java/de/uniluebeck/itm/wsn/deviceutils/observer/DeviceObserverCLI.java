/**********************************************************************************************************************
 * Copyright (c) 2010, Institute of Telematics, University of Luebeck                                                 *
 * All rights reserved.                                                                                               *
 *                                                                                                                    *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the   *
 * following conditions are met:                                                                                      *
 *                                                                                                                    *
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following *
 *   disclaimer.                                                                                                      *
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the        *
 *   following disclaimer in the documentation and/or other materials provided with the distribution.                 *
 * - Neither the name of the University of Luebeck nor the names of its contributors may be used to endorse or        *
 *   promote products derived from this software without specific prior written permission.                           *
 *                                                                                                                    *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, *
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE      *
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,         *
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE *
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF    *
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY   *
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                                *
 **********************************************************************************************************************/

package de.uniluebeck.itm.wsn.deviceutils.observer;

import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Guice;
import de.uniluebeck.itm.util.logging.Logging;
import de.uniluebeck.itm.wsn.deviceutils.DeviceUtilsModule;
import de.uniluebeck.itm.wsn.deviceutils.macreader.DeviceMacReferenceMap;
import de.uniluebeck.itm.wsn.drivers.core.MacAddress;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.*;

import static de.uniluebeck.itm.wsn.deviceutils.CliUtils.printUsageAndExit;

public class DeviceObserverCLI {

	private final static Level[] LOG_LEVELS = {Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR};

	private static final Logger log = LoggerFactory.getLogger(DeviceObserverCLI.class);

	private static final int EXIT_CODE_REFERENCE_FILE_NOT_EXISTING = 2;

	private static final int EXIT_CODE_REFERENCE_FILE_NOT_READABLE = 3;

	private static final int EXIT_CODE_REFERENCE_FILE_IS_DIRECTORY = 4;

	public static void main(String[] args) throws IOException {

		Logging.setLoggingDefaults();

		CommandLineParser parser = new PosixParser();
		Options options = createCommandLineOptions();

		DeviceMacReferenceMap deviceMacReferenceMap = null;

		try {

			CommandLine line = parser.parse(options, args, true);

			if (line.hasOption('h')) {
				printUsageAndExit(DeviceObserverCLI.class, options, 0);
			}

			if (line.hasOption('v')) {
				org.apache.log4j.Logger.getRootLogger().setLevel(Level.DEBUG);
			}

			if (line.hasOption('l')) {
				Level level = Level.toLevel(line.getOptionValue('l'));
				org.apache.log4j.Logger.getRootLogger().setLevel(level);
			}

			if (line.hasOption('r')) {
				deviceMacReferenceMap = readDeviceMacReferenceMap(line.getOptionValue('r'));
			}

		} catch (Exception e) {
			log.error("Invalid command line: " + e);
			printUsageAndExit(DeviceObserverCLI.class, options, 1);
		}

		final ExecutorService executorService = Executors.newCachedThreadPool(
				new ThreadFactoryBuilder().setNameFormat("DeviceObserver %d").build()
		);
		
		final DeviceObserver deviceObserver = Guice
				.createInjector(new DeviceUtilsModule(executorService, deviceMacReferenceMap))
				.getInstance(DeviceObserver.class);

		deviceObserver.addListener(new DeviceObserverListener() {
			@Override
			public void deviceEvent(final DeviceEvent event) {
				System.out.println(event);
			}
		});

		final ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("DeviceObserverScheduler %d").build();
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, threadFactory);
		scheduler.scheduleAtFixedRate(deviceObserver, 0, 1, TimeUnit.SECONDS);
	}

	private static DeviceMacReferenceMap readDeviceMacReferenceMap(final String fileName) throws IOException {

		final DeviceMacReferenceMap deviceMacReferenceMap;
		final File referenceToMacMapPropertiesFile = new File(fileName);

		if (!referenceToMacMapPropertiesFile.exists()) {
			log.error("Reference file {} does not exist!");
			System.exit(EXIT_CODE_REFERENCE_FILE_NOT_EXISTING);
		} else if (!referenceToMacMapPropertiesFile.canRead()) {
			log.error("Reference file {} is not readable!");
			System.exit(EXIT_CODE_REFERENCE_FILE_NOT_READABLE);
		} else if (referenceToMacMapPropertiesFile.isDirectory()) {
			log.error("Reference file {} is a directory!");
			System.exit(EXIT_CODE_REFERENCE_FILE_IS_DIRECTORY);
		}

		Properties properties = new Properties();
		properties.load(new FileInputStream(referenceToMacMapPropertiesFile));

		deviceMacReferenceMap = new DeviceMacReferenceMap();

		for (Object key : properties.keySet()) {
			final String value = (String) properties.get(key);
			deviceMacReferenceMap.put((String) key, new MacAddress(value));
		}

		return deviceMacReferenceMap;
	}

	private static Options createCommandLineOptions() {

		Options options = new Options();

		// add all available options
		options.addOption("r", "referencetomacmap", true,
				"Optional: a properties file containing device references to MAC address mappings"
		);
		options.addOption("v", "verbose", false, "Optional: verbose logging output (equal to -l DEBUG)");
		options.addOption("l", "logging", true,
				"Optional: set logging level (one of [" + Joiner.on(", ").join(LOG_LEVELS) + "])"
		);
		options.addOption("h", "help", false, "Optional: print help");

		return options;
	}

}
