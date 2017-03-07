package gov.usgs.owi.nldi.basin;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.support.SqlSessionDaoSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class Dao extends SqlSessionDaoSupport {

	@Autowired
	public Dao(SqlSessionFactory sqlSessionFactory) {
		setSqlSessionFactory(sqlSessionFactory);
	}

	public BigInteger truncateBasinTemp() {
		return getSqlSession().selectOne("basin.truncateBasinTemp");
	}

	public int buildBasins(int minSize, int maxSize, String[] region) {
		Map<String, Object> parameterMap = new HashMap<>();
		parameterMap.put("minSize", minSize);
		parameterMap.put("maxSize", maxSize);
		if (null != region) {
			parameterMap.put("region", region);
		}
		return getSqlSession().insert("basin.buildBasins", parameterMap);
	}

	public int copyBasins() {
		return getSqlSession().update("basin.copyBasins");
	}

	public int updateStartFlags() {
		return getSqlSession().update("basin.updateStartFlags");
	}

}
