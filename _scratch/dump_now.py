import ui_walk as u
xml = u.dump()
nodes = u.nodes(xml)
with open("ui_dump_now.txt", "w", encoding="utf-8") as f:
    for n in nodes:
        if n["text"] or n["desc"]:
            f.write("%r / %r @ %s,%s click=%s\n" % (n["text"], n["desc"], n["cx"], n["cy"], n["clickable"]))
print("nodes", len(nodes))
