# Basin Boundary Calculation

The code in this repository can be used to calculate basin boundaries for most HUC12 Pour Point (HUC12PP) Catchments. At this time is is manual process which needs a fair amount of hands-on effort to complete.
This process also requires a dedicated database as it will destroy the operational data during execution.

##Overview
The PostGIS function __st_union__ can merge the catchment boundaries (found in nhdplus.catchmentsp) into a single geom value. However, it cannot work miracles and takes too long to process the typical requests expected.
We have found that reasonable response times are possible when combining fewer than 4500 catchments. It was decided that precalculating the HUC12PP would allow us to meet the performance goals.
The existing upstream with tributaries navigation logic could also be reused by simply marking the HUC12PP as the starting point of the tributary.

Eight table are used in the calculations.
* Four are the raw input data and only used to seed the process. They are created with the NLDI Liquibase scripts and populated via the load scripts or ingest process:
  * nhdplus.plusflowlinevaa_np21
  * nhdplus.catchmentsp
  * nldi\_data.feature\_huc12pp
  * characteristic\_data.total\_accumulated\_characteristics
* Two are temporary and only exist to support the calculation effort:
  * characteristic\_data.catchmentsp\_temp
  * characteristic\_data.feature\_huc12pp
* Two are permanent and used to support the basin boundary queries. They are created with the NLDI Liquibase scripts, and (other than during this effort) populated via the load scripts:
  * characteristic\_data.plusflowlinevaa\_np21
  * characteristic\_data.catchmentsp

##Setup
* Make sure database is up-to-date with Liquibase.
* Truncate the characteristic\_data.catchmentsp table.
* Truncate the characteristic\_data.plusflowlinevaa\_np21 table.
* Seed the characteristic\_data.catchmentsp table from nhdplus.catchmentsp:

```sql
insert into characteristic_data.catchmentsp
select ogc_fid, featureid, the_geom
from nhdplus.catchmentsp;
```

* Seed the characteristic_data.plusflowlinevaa_np21 from nhdplus.plusflowlinevaa_np21:

```sql
insert into characteristic_data.plusflowlinevaa_np21
select comid, hydroseq, startflag, dnhydroseq, dnminorhyd, pathlength
  from nhdplus.plusflowlinevaa_np21;
```

* Create the temporary table characteristic\_data.catchmentsp\_temp:

```sql
create table characteristic_data.catchmentsp_temp
(comid		integer not null
,the_geom	geometry(MultiPolygon,4269)
);
alter table characteristic_data.catchmentsp_temp owner to nldi_data;
```

* Create and populate the temporary table characteristic\_data.feature\_huc12pp:

```sql
create table characteristic_data.feature_huc12pp
(comid			integer not null
,identifier		character varying(500)
,characteristic_value	numeric(20, 10)
);
alter table characteristic_data.feature_huc12pp owner to nldi_data;

insert into characteristic_data.feature_huc12pp
select total_accumulated_characteristics.comid, feature_huc12pp,.identifier, total_accumulated_characteristics.characteristic_value
  from characteristic_data.total_accumulated_characteristics
       join nldi_data.feature_huc12pp
         on total_accumulated_characteristics.comid = feature_huc12pp.comid and
            total_accumulated_characteristics.characteristic_id = 'TOT_BASIN_AREA';
create index feature_huc12pp_value on characteristic_data.feature_huc12pp(characteristic_value);
```

* Deploy the war to a Tomcat container.
  * Three entries are needed in the context.xml (be sure to replace your actual values for the handlebars):

```xml
<Environment name="jms/brokerURL" type="java.lang.String" value="tcp://{{ACTIVEMQ_HOST}}:61616"/>
<Environment name="jms/nldiBasinBoundary" type="java.lang.String" value="{{QUEUE_NAME}}"/>
<Resource name="jdbc/NLDI" auth="Container" type="javax.sql.DataSource" driverClassName="org.postgresql.Driver" url="jdbc:postgresql://{{POSTGRES_HOST}}:5432/nldi" username="nldi_data" password="{{PASSWORD}}" maxTotal="20" maxIdle="10" maxWaitMillis="-1"/>

```

##Calculating the Basin Boundaries
The routine attempts to calculate HUC12PPs from upstream to downstream (based on TOT\_BASIN\_Area). This should minimize the number of catchments joined in any given iteration of the processing loop. A rudimentary checkpoint restart function was also implemented.
The calculation is invoked via a message queue (in our case ActiveMQ). A message is sent to the queue containing four comma delimited values:

```
minArea,maxArea,step,region
```

__minArea__ The smallest TOT_BASIN_AREA to process in this run. __Required__

__maxArea__ Only TOT_BASIN_AREA values less than this value will be processed in this run. __Required__

__step__ The size of each iteration's window. Note that if a step value does not end on a maxArea, that window will not be processed. __Required__

__region__ Untested feature to limit a run to a HUC2. Note that picking a HUC2 drained by the Colorado or Mississippi will automatically process all HUC2s for that river system. __Optional__

For example:
* 1,10,2 will process HUC12PPs having a TOT_BASIN_AREA greater than or equal to 1 and less than 10 in five loops.
* 1,10,6 will process HUC12PPs having a TOT_BASIN_AREA greater than or equal to 1 and less than 7 in one loop. (The second window would be >= 7 and < 13 which extends beyond the maxArea of 10.)
* 1,10,2,14 will process HUC12PPs in the Upper and Lower Colorado regions (14 and 15) having a TOT_BASIN_AREA greater than or equal to 1 and less than 10 in five loops.

##Next Steps
Assuming the entire dataset is process, you now have two tables to use in displaying basin boundaries:
* characteristic\_data.plusflowlinevaa\_np21 - Used for the UT navigation, modified with startflag set to one on all HUC12PP comids with precalculated basin boundaries.
* characteristic\_data.catchmentsp - Catchment boundaries modified with basin boundaries for all HUC12PP comids with precalculated basins.
These tables can be dumped and loaded into other databases using the project's established methods.
