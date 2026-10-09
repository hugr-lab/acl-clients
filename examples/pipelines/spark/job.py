"""A Spark step that reads a virtual table through a duckdb-acl node and writes back (spec 004).

Everything a node needs comes from the driver: the token (ACL_TOKEN) and the lineage parent, which the
driver takes from OPENLINEAGE_PARENT_ID - the same parent Spark's OpenLineage listener reports, so the
node's runs and Spark's land under one step of the orchestrator.
"""
import os
from pyspark.sql import SparkSession, functions as F

spark = SparkSession.builder.appName("acl_orders_by_tenant").getOrCreate()
url = "jdbc:acl://" + os.environ.get("ACL_DOOR", "host.docker.internal:32800")
props = {
    "driver": "io.github.hugrlab.acl.jdbc.AclDriver",
    "flow": "token", "token": os.environ["ACL_TOKEN"],
    # the dev node's certificate is self-signed; point tlsRootCerts at a CA bundle instead in production
    "disableCertificateVerification": "true",
}

# names in three parts: <virtual catalog>.<schema>.<table> - the name the node's lineage gives the table
orders = spark.read.format("jdbc").option("url", url).options(**props) \
    .option("query", "SELECT id, tenant, amount FROM sales.main.orders").load()
totals = orders.groupBy(F.col("id").cast("int").alias("id")).agg(F.sum("amount").cast("double").alias("total"))
totals.show()

# append: overwrite would drop and create the table (needs `drop` + `create` on its schema), and
# Spark's `truncate` needs a JDBC dialect the driver does not offer
totals.write.format("jdbc").option("url", url).options(**props) \
    .option("dbtable", "sales.main.spark_out").mode("append").save()
print("DONE")
spark.stop()
