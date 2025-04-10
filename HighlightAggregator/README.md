# HighlightAggregator
Take exported highlights from an eBook reader and format
the highlights in a much more readable and succinct manner.

- Perform basic actions on the HTML
   - add, remove, edit CSS class styles
   - add, remove, edit HTML text such as reader notes and highlights
- Aggregate proximal highlights
   - specify merge distance via chapters, pages or locations or all
   - number means distance (from previous item, from start of aggregation)
   - specify aggregation location (page 14, locations 143-176) or (chapter 1, pages 1-22, locations 1-240)
   - aggregation emit - container options, lists/bullets, paragraphs, tables/rows, Moustache templates, others