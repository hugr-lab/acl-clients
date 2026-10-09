-- append is the incremental strategy that works through quack: delete+insert (a unique_key) needs a
-- DELETE through the attached catalog, which the quack client does not plan ("PlanDelete not implemented")
{{ config(materialized='incremental', incremental_strategy='append') }}
select id, amount from {{ source('sales', 'orders') }}
{% if is_incremental() %} where id > (select coalesce(max(id), -1) from {{ this }}) {% endif %}
