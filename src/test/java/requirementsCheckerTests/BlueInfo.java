package requirementsCheckerTests;

import lsp.functions.*;

public class BlueInfo extends LSPInfo {

	private LSPInfoFunction blueInfoFunction;
	
	public BlueInfo() {
		blueInfoFunction = LSPInfoFunctionUtils.createDefaultInfoFunction();
		LSPInfoFunctionValue<String> value = LSPInfoFunctionUtils.createInfoFunctionValue("blue" );
		value.setValue("blue");
		blueInfoFunction.getValues().add(value);
	}
	
	@Override
	public String getName() {
		return "blue";
	}

	@Override
	public LSPInfoFunction getFunction() {
		return blueInfoFunction;
	}

	@Override
	public double getFromTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getToTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void update() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setName(String name) {
		// TODO Auto-generated method stub
		
	}

}
