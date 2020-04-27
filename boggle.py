# -*- coding: utf-8 -*-

"""
Boggle solver.

Tool to find solutions to a Boggle board and query valid words. Uses NASPA Word List 2018.

Example:
        $ python boggle.py -l a b c d e f g h i j k l m n o p
        $ python boggle.py -v word

Attributes:
    WORDS ({str}): set containing all valid scrabble words.
    THREE_STARTERS({str}): set containing all valid three-character beginnings to words.
            used to optimize word validation.
"""


# Module imports
import argparse


with open('nwl2018.txt') as f:  # NASPA Word List 2018
    WORDS = set()
    THREE_STARTERS = set()  # hashing to optimize 'contains' operations
    for line in f.readlines():
        word = line.strip().lower()
        WORDS.add(word)
        if len(word) >= 3:
            THREE_STARTERS.add(word[:3])  # first three characters


class Boggle():
    """Represents board graph. Can solve board and validate words."""
    __word_dict = WORDS  # set containing all valid Boggle words

    def __init__(self, symbols, size=4):
        """
        [Validates input and builds graph]

        Args:
            symbols ([str]): [List of symbols on Boggle board. Must match specified dimensions.
                If size = 4, then 16 symbols must be passed]
            size (int, optional): [Boggle board is {size} x {size} grid]. Defaults to 4.

         Raises:
            TypeError: [Symbols is not iterable]
            ValueError: [Size is not an integer.]
            ValueError: [Number of symbols passed does not match board size.]
        """

        try:
            symbols = [str(sym).strip().lower() for sym in symbols]
        except TypeError:
            raise TypeError(
                f"Failed to parse symbols in Boggle constructor. {symbols} was not iterable")

        try:
            size = int(size)
        except ValueError:
            raise ValueError(
                f"Error in instantiating Boggle object. {size} could not be cast to int.")

        if len(symbols) != size * size:
            raise ValueError(
                f"Symbols do not match board size. For size {size}, should have {size * size} symbols.\nReceived {len(symbols)}: {symbols}.")

        self.size = size
        self.adj_list = []

        for i, symbol in enumerate(symbols):
            x_position = int(i / self.size)
            y_position = i % self.size
            edges = []

            new_node = Node(x_position, y_position, edges, symbol)
            self.adj_list.append(new_node)

            for node in self.adj_list:
                if abs(x_position - node.x_position) <= 1 and \
                        abs(y_position - node.y_position) <= 1:

                    # one unit away

                    new_node.edges.append(node)
                    node.edges.append(new_node)

    @property
    def adj_list(self):
        """
        [Adjacency list representation for Boggle board graph]

        Returns:
            [Node]: [List of Nodes on Boggle board.]
        """
        return self._adj_list

    @adj_list.setter
    def adj_list(self, val):
        """
        [Updates adjacency list.]

        Args:
            val ([Node]): [List of Nodes on Boggle board]

        Raises:
            ValueError: [val does not contain only Node instances]
            TypeError: [val is not iterable]
        """
        try:

            if not all(isinstance(x, Node) for x in val):
                raise ValueError(
                    "Adjacency list in Boggle object must only contain Node instances")

            self._adj_list = val

        except TypeError as t_err:
            raise TypeError(
                f"Failed to initialize Boggle adjacency list. {val} not iterable.")

    @property
    def size(self):
        """
        [Size of Boggle board.]

        Returns:
            int: [Size of Boggle board.]
        """
        return self._size

    @size.setter
    def size(self, val):
        """
        [Updates size.]

        Args:
            val (int): [Size of Boggle board]

        Raises:
            ValueError: [val cannot be converted to int.]
            ValueError: [val isn't a positive integer.]
        """
        try:

            val = int(val)

        except ValueError:
            raise ValueError(
                f"Boggle board size {val} failed to be cast to an integer.")

        if val <= 0:
            raise ValueError(f"Size of Boggle board must be > 0. Got {val}.")

        self._size = val

    @classmethod
    def word_validator(cls, word_to_check):
        """
        [Check if a word is in the classes __WORDS set]

        Args:
            word_to_check (str): [Word to validate]

        Returns:
            bool: [True if word is valid.]
        """
        return word_to_check in cls.__word_dict

    def find_words(self):
        """
        [Find and print all words in the Boggle board sorted by size (3 - 10 letters) and alphabet.]
        """

        valid_words = {'3': set(), '4': set(), '5': set(),
                       '6': set(), '7': set(), '8': set(), '9': set(), '10': set()}

        for node in self._adj_list:
            self.extend_and_check_words(node.symbol, [node], valid_words)

        for key, val in valid_words.items():
            valid_words[key] = sorted(list(val))
            # convert from sets to sorted lists

        return valid_words

    def extend_and_check_words(self, word_to_extend, node_list, valid_words):
        """
        [Given a word built from the nodes previously visited, validates it and
            extends it with all unvisited neighboring nodes of most recently visited node.
            Can halt recursive calls when word is 10 characters or more. If word is of length three,
            checks if that is a prefix to any valid words. If not, makes no more calls.]

        Args:
            word_to_extend (str): [Word to validate and/or extend.]
            node_list ([Node]): [Previously visited nodes (in order) that correspond to
                symbols in word_to_extend]
            valid_words (dict): [Contains all previously found words organized by length
                (3 - 10 characters)]
        """
        good_starter = True
        # set to false if we have a three-letter word that doesn't prefix any valid words
        if len(word_to_extend) == 3:
            good_starter = word_to_extend[:3] in THREE_STARTERS

        if good_starter:
            if len(word_to_extend) >= 3:  # then we validate the word
                if self.word_validator(word_to_extend):
                    valid_words[str(len(word_to_extend))].add(word_to_extend)
                    for node in node_list:  # nodes have all been involved in building a valid word
                        node.usage_count += 1

            if len(word_to_extend) < 10:  # then we extend the word
                for neighbor in node_list[-1].edges:  # last node visited
                    if neighbor not in node_list:  # has not been visited yet
                        # make new list to avoid pointer issues
                        new_node_list = node_list + [neighbor]
                        self.extend_and_check_words(
                            word_to_extend+neighbor.symbol, new_node_list, valid_words)

    def __repr__(self):
        rep = f""
        for row in range(self.size):
            for col in range(self.size):
                sym = self._adj_list[row * self.size +
                                     col].symbol  # add symbol to string
                # ensure each symbol starts five characters over from the previous one
                rep += sym + " "*(5 - len(sym))
            rep += "\n"
        return rep


class Node():
    """
    Single node in Boggle board.
    """

    def __init__(self, x, y, edges, symbol):
        """
        [Sets Node's position, neighboring Nodes, symbol, and sets usage to zero.]

        Args:
            x (int): [X-coordinate on Boggle board.]
            y (int): [Y-coordinate on Boggle board.]
            edges ([Node]): [Nodes immediately adjacent or diagonal to this Node]
            symbol (str): [Symbol this Node represents]
        """

        self.x_position = x
        self.y_position = y
        self.edges = edges
        self.symbol = symbol
        self.usage_count = 0  #

    @property
    def x_position(self):
        """
        [X-coordinate on Boggle board.]

        Returns:
            int: [X-coordinate on Boggle board.]
        """
        return self._x_position

    @x_position.setter
    def x_position(self, val):
        """
        [Updates x-coordinate of Node.]

        Args:
            val (int): [New x-coordinate of Node.]

        Raises:
            ValueError: [Failed to cast val to an int.]
            ValueError: [Val is negative.]
        """

        try:
            val = int(val)
        except ValueError:
            raise ValueError(
                f"Failed to set x-coordinate of Node. {val} could not be cast to int.")

        if val < 0:
            raise ValueError(f"X-coordinate of Node must be >= 0. Got {val}")

        self._x_position = val

    @property
    def y_position(self):
        """
        [Y-coordinate on Boggle board.]

        Returns:
            int: [Y-coordinate on Boggle board.]
        """

        return self._y_position

    @y_position.setter
    def y_position(self, val):
        """
        [Updates y-coordinate of Node.]

        Args:
            val (int): [New y-coordinate of Node.]

        Raises:
            ValueError: [Failed to cast val to an int.]
            ValueError: [Val is negative.]
        """

        try:
            val = int(val)
        except ValueError:
            raise ValueError(
                f"Failed to set y-coordinate of Node. {val} could not be cast to int.")

        if val < 0:
            raise ValueError(f"Y-coordinate of Node must be >= 0. Got {val}")

        self._y_position = val

    @property
    def edges(self):
        """
        [Nodes immediately adjacent or diagonal to this Node.
            Their x- and y- coordinates differ by <= 1.]

        Returns:
            [Node]: [Nodes immediately adjacent or diagonal to this Node.]
        """
        return self._edges

    @edges.setter
    def edges(self, val):
        """
        [Update edges.]

        Args:
            val ([Node]): [New list of Nodes immediately adjacent or diagonal to this one.]

        Raises:
            ValueError: [Not all elements in val are of type Node]
            TypeError: [val is not iterable]
        """
        try:
            if not all(isinstance(x, Node) for x in val):
                raise ValueError(f"Failed to update edges of Node. Not all elements in {val} \
                    were of class Node.")
        except TypeError:
            raise TypeError(
                f"Failed to update edges of Node. {val} not iterable.")

        self._edges = val

    @property
    def symbol(self):
        """
        [Symbol this Node represents.]

        Returns:
            str: [Symbol this Node represents.]
        """
        return self._symbol

    @symbol.setter
    def symbol(self, val):
        self._symbol = str(val)

    @property
    def usage_count(self):
        """
        [How many times this node is used in a valid word.]

        Returns:
            int: [How many times this node is used in a valid word.]
        """
        return self._usage_count

    @usage_count.setter
    def usage_count(self, val):

        try:
            val = int(val)
        except ValueError:
            raise ValueError(
                f"Error in setting usage count. {val} failed to be cast to int.")
        if val < 0:
            raise ValueError(
                f"Error in setting usage count. Val must not be negative. Got {val}.")

        self._usage_count = val

    def __repr__(self):

        adjacent_symbols = []
        for neighbor in self.edges:
            adjacent_symbols.append(neighbor.symbol)

        return f"SYMBOL: {self.symbol} \nX: {self.x_position} \nY: {self.y_position} \
            \nNEIGHBORING SYMBOLS: {adjacent_symbols}"

    def reset(self):
        """
        [Resets Node's usage count.]
        """
        self.usage_count = 0


if __name__ == "__main__":

    PARSER = argparse.ArgumentParser(
        prog="boggle.py", description='Boggle game companion')

    PARSER.add_argument('-l', '--list', nargs='+', dest='syms',
                        help='list of symbols (separated by space) to populate Boggle grid',
                        metavar='L')
    # assembles ensuing arguments into a list of strings

    PARSER.add_argument('-v', '--val', dest='val',
                        help='pass a word to validate', metavar='V')
    # takes a single string argument

    ARGS = PARSER.parse_args()
    if ARGS.val:  # if -v was passed
        print(Boggle.word_validator(ARGS.val))
    if ARGS.syms:  # if -l was passed
        BOARD = Boggle(ARGS.syms)
        valid_words = BOARD.find_words()
        for key, val in valid_words.items():
            print(key + " LETTERS: ", sorted(val))
