package org.brewchain.account.core.processor;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Instantiate(name = "Processor_Manager")
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Slf4j
@Data
public class ProcessorManager implements ActorService {
	@ActorRequire(name = "Default_Processor", scope = "global")
	DefaultProcessor defaultProcessor;

	@ActorRequire(name = "V1_Processor", scope = "global")
	V1Processor v1Processor;

	public IProcessor getProcessor(int version) {
		if (version == 0) {
			return defaultProcessor;
		} else if (version == 1) {
			return v1Processor;
		} else {
			throw new RuntimeException("worng account version::" + version);
		}
	}
}
