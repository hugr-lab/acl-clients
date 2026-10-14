"""The definition lineage of duckdb-acl spec 129 planned as OpenMetadata edges (acl-clients spec 005)."""

import pytest

from acl_openmetadata.node import NodeOptions
from acl_openmetadata.source import column_fqn, plan_lineage, source_fqn


def row(obj, field, src_ns, src_name, src_field, type_="DIRECT", catalog="sales"):
    return {"catalog": catalog, "schema": "main", "object": obj, "object_type": "TABLE", "namespace": "acl://dev",
            "field": field, "source_namespace": src_ns, "source_name": src_name, "source_field": src_field,
            "type": type_, "subtype": "IDENTITY", "masking": False, "approximate": False}


def test_a_physical_source_under_its_mapped_prefix():
    edges, unmapped = plan_lineage(
        [row("orders", "id", "acl://dev/source/memory", "main.orders", "id"),
         row("orders", "amount", "acl://dev/source/memory", "main.orders", "amount"),
         row("orders", "", "acl://dev/source/memory", "main.orders", "tenant", type_="INDIRECT")],
        "acl_dev", {"acl://dev/source/memory": "mem.memory"})
    assert not unmapped and len(edges) == 1
    edge = edges[0]
    assert edge.target == "acl_dev.sales.main.orders" and edge.source == "mem.memory.main.orders"
    # the INDIRECT edge to the whole object draws the table edge, no column
    assert edge.columns == {"acl_dev.sales.main.orders.id": {"mem.memory.main.orders.id"},
                            "acl_dev.sales.main.orders.amount": {"mem.memory.main.orders.amount"}}


def test_a_virtual_source_is_the_nodes_own_and_an_unmapped_one_is_reported():
    own = row("totals", "total", "acl://dev", "sales.main.orders", "amount")
    assert source_fqn(own, "acl_dev", {}) == "acl_dev.sales.main.orders"
    edges, unmapped = plan_lineage([row("orders", "id", "postgres://pg:5432", "shop.main.orders", "id")],
                                   "acl_dev", {})
    assert edges == [] and unmapped == {"postgres://pg:5432"}


def test_a_nested_field_path_is_a_nested_column():
    assert column_fqn("s.c.main.customers", "address.city") == "s.c.main.customers.address.city"
    assert column_fqn("s.c.main.customers", "visits[].city") == "s.c.main.customers.visits.city"


def test_a_name_with_a_dot_is_quoted():
    own = row("t", "x", "acl://dev", 'sales."raw.eu".events', "x")
    assert source_fqn(own, "acl_dev", {}) == 'acl_dev.sales."raw.eu".events'


def test_no_credential_is_a_value_of_the_configuration():
    for forbidden in ("token", "clientSecret", "password", "secret"):
        with pytest.raises(ValueError, match="refused"):
            NodeOptions.from_options({"uri": "grpc+tls://h:1", forbidden: "x"})
    options = NodeOptions.from_options({"uri": "grpc+tls://h:1", "insecure": "true",
                                        "sourceServices": '{"a": "b"}'})
    assert options.insecure and options.source_services == {"a": "b"} and options.token_env == "ACL_NODE_TOKEN"
