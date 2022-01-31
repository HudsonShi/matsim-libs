package lsp.replanning;

import org.matsim.core.replanning.GenericStrategyManager;

import lsp.LSP;
import lsp.LSPPlan;

public interface LSPPlanStrategyManagerFactory {
	
	GenericStrategyManager<LSPPlan, LSP> createStrategyManager(LSP lsp);

}
