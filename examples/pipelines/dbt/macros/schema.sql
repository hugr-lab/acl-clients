{# dbt prefixes a custom schema with the target's by default (main_dbt_home); the node's schema is dbt_home #}
{% macro generate_schema_name(custom_schema_name, node) -%}{{ custom_schema_name or target.schema }}{%- endmacro %}
