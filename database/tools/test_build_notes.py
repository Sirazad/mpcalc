"""Tests of build_notes.py (standard library only). Run from database/:  python -m unittest discover -s tools -p "test_*.py" """
import unittest

import build_notes as b


def note(id, links=(), **kw):
    n = {"id": id, "title": id.title(), "category": "magic", "school": None, "keywords": [id], "links": list(links),
         "text": "szöveg", "source": "forrás", "file": f"{id}.md"}
    n.update(kw)
    return n


class LinkTests(unittest.TestCase):
    def test_a_declared_link_is_added_to_the_other_note_and_the_declared_one_stays_first(self):
        a, c, d = note("a", ["b"]), note("b", ["c"]), note("c")
        b.add_reverse_links([a, c, d])
        self.assertEqual(a["links"], ["b"])
        self.assertEqual(c["links"], ["c", "a"])
        self.assertEqual(d["links"], ["b"])

    def test_a_link_declared_on_both_sides_is_not_doubled(self):
        a, c = note("a", ["b"]), note("b", ["a"])
        b.add_reverse_links([a, c])
        self.assertEqual((a["links"], c["links"]), (["b"], ["a"]))

    def test_running_it_twice_changes_nothing(self):
        a, c = note("a", ["b"]), note("b")
        b.add_reverse_links([a, c]); b.add_reverse_links([a, c])
        self.assertEqual((a["links"], c["links"]), (["b"], ["a"]))

    def test_a_link_to_a_missing_note_a_self_link_and_a_duplicate_are_errors(self):
        errors = b.check([note("a", ["nincs"]), note("b", ["b"]), note("c", ["a", "a"])])
        text = "\n".join(errors)
        self.assertIn("link to 'nincs', but there is no such note", text)
        self.assertIn("cannot link to itself", text)
        self.assertIn("duplicate link", text)

    def test_notes_with_good_links_pass(self):
        self.assertEqual(b.check([note("a", ["b"]), note("b")]), [])

    def test_the_migration_carries_the_links(self):
        a, c = note("a", ["b"]), note("b")
        b.add_reverse_links([a, c])
        sql = b.migration([a, c])
        self.assertIn("'a', 'A', 'magic', NULL, 'a', 'b'", sql)
        self.assertIn("'b', 'B', 'magic', NULL, 'b', 'a'", sql)
        self.assertIn("links = EXCLUDED.links", sql)


class HeaderTests(unittest.TestCase):
    def test_links_are_an_optional_header_field_read_as_a_comma_list(self):
        import tempfile, pathlib
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d) / "x.md"
            p.write_text("---\nid: x\ntitle: X\ncategory: magic\nkeywords: x\nsource: s\nlinks: y, z\n---\ntext\n", encoding="utf-8")
            self.assertEqual(b.parse(p)["links"], ["y", "z"])
            p.write_text("---\nid: x\ntitle: X\ncategory: magic\nkeywords: x\nsource: s\n---\ntext\n", encoding="utf-8")
            self.assertEqual(b.parse(p)["links"], [])


if __name__ == "__main__":
    unittest.main()
