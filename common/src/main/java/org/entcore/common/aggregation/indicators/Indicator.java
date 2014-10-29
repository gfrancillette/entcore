package org.entcore.common.aggregation.indicators;

import java.util.ArrayList;
import java.util.Collection;

import org.entcore.common.aggregation.filters.IndicatorFilter;
import org.entcore.common.aggregation.groups.IndicatorGroup;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

/**
 * An Indicator is an object used to aggregate into a single value multiple ent-core traces from Mongo and write the result as multiple Mongo documents.<br>
 * <br>
 * Indicators contain two collections, Filters and Groups :
 * <ul>
 * 	<li> Filters are used to filter traces (like by date, or user) </li>
 *  <li> Groups are used to group the aggregation by criteria (like the SQL group by clause) </li>
 * </ul>
 * By default, indicators only aggregate traces with the database type field equal to the String passed to the constructor.
 *
 */
public abstract class Indicator{

	//Indicator key - must match a trace collection type
	protected final String indicatorKey;
		
	//Filters and groups
	protected Collection<IndicatorFilter> filters;
	protected Collection<IndicatorGroup> groups;
	
	/**
	 * Creates a new Indicator without filters or groups.<br>
	 * @param key : Traces will be filtered using this String and an equality check against the type of trace.
	 */
	protected Indicator(String key){
		this.indicatorKey = key;
		this.filters = new ArrayList<IndicatorFilter>();
		this.groups = new ArrayList<IndicatorGroup>();
	}
	/**
	 * Creates a new Indicator with filters and / or groups.<br>
	 * @param key : Traces will be filtered using this String and an equality check against the type of trace.
	 * @param filters : Filters are used to filter traces further than the default type filtering.
	 * @param groups : Groups are used to group results by several keys or combination of keys.
	 */
	protected Indicator(String key, Collection<? extends IndicatorFilter> filters, Collection<IndicatorGroup> groups){
		this.indicatorKey = key;
		this.filters = new ArrayList<>(filters);
		this.groups = groups;
	}
	
	/* GETTERS */
	
	/**
	 * Returns the indicator key.
	 * @return Indicator key
	 */
	protected String getKey(){
		return indicatorKey;
	}
	
	/**
	 * Returns the collection of filters.
	 * @return Indicator filters.
	 */
	protected Collection<IndicatorFilter> getFilters(){
		return filters;
	}
	/**
	 * Returns the collection of groups.
	 * @return Indicator groups.
	 */
	protected Collection<IndicatorGroup> getGroups(){
		return groups;
	}
	
	/**
	 * Add a new indicator filter to the collection.
	 * @param filter Filter to add.
	 * @return This object.
	 */
	public Indicator addFilter(IndicatorFilter filter){
		filters.add(filter);
		return this;
	}
	/**
	 * Add a new group to the collection.
	 * @param group Group to add.
	 * @return This object.
	 */
	public Indicator addGroup(IndicatorGroup group){
		groups.add(group);
		return this;
	}
	
	/**
	 * Aggregation method.
	 * @param callBack : Handler called when processing is over.
	 */
	public abstract void aggregate(Handler<Message<JsonObject>> callBack);
}
