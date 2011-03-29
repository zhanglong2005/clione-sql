package tetz42.clione.lang;

import static org.junit.Assert.*;
import static tetz42.test.Util.*;

import org.junit.Test;

public class ClioneFactoryTest {

	@Test
	public void testParseByEmpty() {
		Clione clione = ClioneFactory.get().parse("");
		assertNull(clione);
	}

	@Test
	public void param() {
		Clione clione = ClioneFactory.get().parse("KEY");
		assertEqualsWithFile(clione, getClass(), "param");
	}

	@Test
	public void param_literal() {
		Clione clione = ClioneFactory.get().parse("KEY :LITERAL");
		assertEqualsWithFile(clione, getClass(), "param_literal");
	}

	@Test
	public void param_doller() {
		Clione clione = ClioneFactory.get().parse("$KEY :LITERAL");
		assertEqualsWithFile(clione, getClass(), "param_doller");
		clione = ClioneFactory.get().parse("$!KEY PARAM");
		assertEqualsWithFile(clione, getClass(), "param_doller2");
	}

}
