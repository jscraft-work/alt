ALTER TABLE trade_order_intent
    ADD COLUMN symbol_name varchar(200);

UPDATE trade_order_intent oi
   SET symbol_name = am.symbol_name
  FROM asset_master am
 WHERE am.symbol_code = oi.symbol_code;

UPDATE trade_order_intent
   SET symbol_name = '(unknown)'
 WHERE symbol_name IS NULL;

ALTER TABLE trade_order_intent
    ALTER COLUMN symbol_name SET NOT NULL;
