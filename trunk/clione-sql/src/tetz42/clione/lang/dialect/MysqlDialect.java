package tetz42.clione.lang.dialect;

public class MysqlDialect extends Dialect{
	@Override
	public boolean backslashWorkAsEscape() {
		return true;
	}
}
