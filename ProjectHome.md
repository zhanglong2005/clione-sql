# Welcome #
ClioneSQL is a library for generating SQL easily and safety.<br>

<h2>First Sample</h2>
If you use ClioneSQL, you can write find method as follows.<br>
<br>
-method<br>
<pre><code>import static tetz42.clione.SQLManager.*;
              :
	public List&lt;Entity&gt; findAllByAgeAndPref(Integer age, String pref) throws SQLException {

		return sqlManager().useFile(getClass(), "Select.sql")
			.findAll(
			    Entity.class, 
			    params("age", age).$("pref", pref)
			);
	}
</code></pre>
<br>
-'Select.sql'<br>
<pre><code>SELECT
    *
FROM
    people
WHERE
    age = /* $age */25
    AND prefecture = /* $pref */'Tokyo'
</code></pre>
The parameters of 'Select.sql' is specified as SQL comment. So it can be performed by general SQL client tools.<br>
<br>
ClioneSQL replaces /<code>*</code> $age <code>*</code>/ and /<code>*</code> $pref <code>*</code>/ to place holders as follows:<br>
<pre><code>SELECT
    *
FROM
    people
WHERE
    age = ?
    AND prefecture = ?
</code></pre>
ClioneSQL binds parameters correctlly and perform it.<br>
<br>
<br>
If the parameter, 'age', is null, what will happen?<br>
The SQL is converted to :<br>
<pre><code>SELECT
    *
FROM
    people
WHERE
    prefecture = ?
</code></pre>
The separator, 'AND', is removed automatically.<br>
<br>
How about the case of both parameters are null?<br>
The SQL is converted to :<br>
<pre><code>SELECT
    *
FROM
    people
</code></pre>
The where clause is removed automatically.<br>