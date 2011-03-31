package tetz42.clione.lang;

import tetz42.clione.util.ParamMap;

abstract public class AbstractParam extends Clione {

	protected final Param param;
	protected final boolean isNegative;

	public AbstractParam(String key, boolean isNegative) {
		this.isNegative = isNegative;
		this.param = new Param(key);
	}

	@Override
	public Instruction perform(ParamMap paramMap) {
		return performTask(paramMap, this.param.perform(paramMap));
	}

	protected Instruction performTask(ParamMap paramMap, Instruction paramInst) {
		if (isParamExists(paramInst) ^ isNegative) {
			return caseParamExists(paramMap, paramInst);
		} else {
			return caseParamNotExists(paramMap, paramInst);
		}
	}

	protected Instruction caseParamExists(ParamMap paramMap,
			Instruction paramInst) {
		return getInstruction(paramMap).merge(paramInst);
	}

	protected Instruction caseParamNotExists(ParamMap paramMap,
			Instruction paramInst) {
		paramInst.isNodeRequired = false;
		return paramInst;
	}

	protected final boolean isParamExists(Instruction instruction) {
		for (Object e : instruction.params) {
			if (e != null)
				return true;
		}
		return false;
	}

}
