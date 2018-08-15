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
	
	@ActorRequire(name = "V1_Processor", scope = "global")
	V1Processor v1Processor;
	
	@ActorRequire(name = "V2_Processor", scope = "global")
	V2Processor v2Processor;

	public IProcessor getProcessor(int version) {
		if (version == 1) {
			return v1Processor;
		} else if (version == 2) {
			return v2Processor;
		}else {
			throw new RuntimeException("worng account version::" + version);
		}
	}
}
